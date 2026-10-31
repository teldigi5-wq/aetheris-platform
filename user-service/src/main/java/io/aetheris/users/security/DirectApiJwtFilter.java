package io.aetheris.users.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;

/**
 * Transitional direct-backend JWT boundary for the EXISTING Identity HMAC
 * token contract (Role, scopes, uid, sub, iat, exp). The key is shared in
 * the legacy format, so this is NOT a complete workload-identity / RS256
 * separation. Reject all protected requests if no key was provisioned.
 */
@Component
public final class DirectApiJwtFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/api/users";
    private static final String SIGNED_ID_ATTRIBUTE = "aetheris.verified.uid";
    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public DirectApiJwtFilter(@Value("${AETHERIS_JWT_SECRET:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    // Explicit test-only clock; no @Bean / remote property to bypass checks.
    DirectApiJwtFilter(String secret, Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.key = secret != null && secret.length() >= 32
            ? Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)) : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Protect the *entire* API namespace, not just exact undecorated
        // controller paths. Servlet getRequestURI() retains matrix parameters,
        // while Spring MVC may route /api/users;v=1 to /api/users.
        // Fail closed on unrecognized /api routes; leave health, docs and
        // other non-API resources to their existing security configuration.
        return !routePath(request).startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Misconfigured services remain healthy for diagnostics but no user or
        // audit data is ever exposed without a valid signing key.
        if (key == null) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Signed backend authorization is not configured");
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || auth.length() > 8192 || !auth.startsWith("Bearer ")
                || request.getHeaders("Authorization").hasMoreElements()
                   && java.util.Collections.list(request.getHeaders("Authorization")).size() != 1) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Valid bearer token required");
            return;
        }
        String token = auth.substring(7).trim();
        if (token.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Valid bearer token required");
            return;
        }
        final Claims claims;
        final long signedUid;
        final String role;
        final Set<String> scopes;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String subject = claims.getSubject();
            Object uid = claims.get("uid");
            role = claims.get("role", String.class);
            scopes = verifiedScopes(claims.get("scopes"));
            if (subject == null || subject.isBlank() || subject.length() > 254
                    || role == null || !Set.of("ADMIN", "DEVELOPER", "API_CONSUMER").contains(role)
                    || !(uid instanceof Long || uid instanceof Integer)
                    || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Invalid required signed claims");
            }
            signedUid = ((Number) uid).longValue();
            Instant issued = claims.getIssuedAt().toInstant();
            Instant expires = claims.getExpiration().toInstant();
            if (signedUid <= 0 || issued.isAfter(clock.instant().plusSeconds(30))
                    || !expires.isAfter(issued) || scopes.isEmpty()) {
                throw new IllegalArgumentException("Invalid token identity or time bounds");
            }
        } catch (Exception invalid) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signed access token");
            return;
        }

        String path = routePath(request);
        String method = request.getMethod();
        boolean privileged = role.equals("ADMIN") || role.equals("DEVELOPER");
        boolean permitted = allowedUsers(path, method, signedUid, privileged, scopes);
        if (!permitted) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Permission denied");
            return;
        }
        // Incoming X-Aetheris-* headers are NOT trusted for authorization.
        request.setAttribute(SIGNED_ID_ATTRIBUTE, signedUid);
        chain.doFilter(request, response);
    }

    private static String routePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = request.getContextPath();
        return uri.startsWith(prefix) ? uri.substring(prefix.length()) : uri;
    }

    private static Set<String> verifiedScopes(Object raw) {
        if (!(raw instanceof Collection<?> list) || list.size() > 32) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof String scope) || scope.isBlank() || scope.length() > 80) {
                return Set.of();
            }
            result.add(scope);
        }
        return Set.copyOf(result);
    }

    private static boolean allowedUsers(String path, String method, long signedUid,
                                        boolean privileged, Set<String> scopes) {
        boolean isRead = method.equals("GET") || method.equals("HEAD");
        boolean isWrite = method.equals("POST") || method.equals("DELETE");
        if (path.equals(PREFIX)) {
            if (isRead) return privileged && scopes.contains("users:read");
            return isWrite && privileged && scopes.contains("users:write");
        }
        if (!path.startsWith(PREFIX + "/")) return false;
        String id = path.substring((PREFIX + "/").length());
        // No nested path, traversal, negative, exponent or leading + formats.
        if (!id.matches("[1-9][0-9]{0,18}")) return false;
        final long resourceId;
        try {
            resourceId = Long.parseLong(id);
        } catch (NumberFormatException invalid) {
            return false;
        }
        if (isRead) {
            // IdentityAccount.uid and User.id come from different data models.
            // Equal numbers DO NOT establish ownership/tenant membership.
            // Deny API_CONSUMER object lookup until a real binding is verified.
            return privileged && scopes.contains("users:read");
        }
        return method.equals("DELETE") && privileged && scopes.contains("users:write");
    }

}
