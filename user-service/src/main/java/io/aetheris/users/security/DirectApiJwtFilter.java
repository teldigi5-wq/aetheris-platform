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
import java.security.PublicKey;
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
    private final PublicKey rs256PublicKey;
    private final JwtPinnedRotationVerifier rs256Verifier;
    private final Clock clock;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final boolean strictIssuerAudience;
    private final boolean issuerAudiencePolicyReady;

    /**
     * Explicitly configured issuer+audience hardening is opt-in until Identity
     * issues and Gateway forwards equivalent vetted claims. An incomplete
     * configuration denies ALL protected calls rather than downgrading.
     */
    @Autowired
    public DirectApiJwtFilter(
        @Value("${AETHERIS_JWT_SECRET:}") String secret,
        @Value("${AETHERIS_BACKEND_JWT_ISSUER:}") String issuer,
        @Value("${AETHERIS_BACKEND_JWT_AUDIENCE:}") String audience,
        @Value("${AETHERIS_BACKEND_JWT_RS256_PUBLIC_KEY_PEM:}") String rs256Pem,
        @Value("${AETHERIS_BACKEND_JWT_RS256_KEY_ID:}") String activeKid,
        @Value("${AETHERIS_BACKEND_JWT_RS256_STAGED_PUBLIC_KEY_PEM:}") String stagedPem,
        @Value("${AETHERIS_BACKEND_JWT_RS256_STAGED_KEY_ID:}") String stagedKid) {
        this(secret, issuer, audience, rs256Pem,
             activeKid, stagedPem, stagedKid, Clock.systemUTC());
    }

    // Legacy tests and call sites keep existing behavior until an approved rollout.
    DirectApiJwtFilter(String secret) {
        this(secret, "", "", Clock.systemUTC());
    }

    // Explicit test-only clock; never exposed as a bean or remote bypass.
    DirectApiJwtFilter(String secret, Clock clock) {
        this(secret, "", "", clock);
    }

    DirectApiJwtFilter(String secret, String issuer, String audience, Clock clock) {
        this(secret, issuer, audience, "", "", "", "", clock);
    }

    DirectApiJwtFilter(String secret, String issuer, String audience,
                       String rs256Pem, Clock clock) {
        this(secret, issuer, audience, rs256Pem, "", "", "", clock);
    }

    DirectApiJwtFilter(String secret, String issuer, String audience,
                       String rs256Pem, String activeKid, String stagedPem,
                       String stagedKid, Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.key = secret != null && secret.length() >= 32
            ? Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)) : null;
        // Review-only: a configured public key selects exclusive RS256
        // verification. A bad PEM fails service initialization, never HS fallback.
        this.rs256Verifier = new JwtPinnedRotationVerifier(rs256Pem, activeKid,
            stagedPem, stagedKid);
        this.rs256PublicKey = rs256Pem == null || rs256Pem.isEmpty()
            ? null : JwtRs256KeySupport.publicKey(rs256Pem);
        this.expectedIssuer = issuer;
        this.expectedAudience = audience;
        // Even a whitespace-only env value is an explicit, INVALID attempt
        // at configuration. Never treat it as absent and downgrade to legacy.
        boolean hasIssuer = issuer != null && !issuer.isEmpty();
        boolean hasAudience = audience != null && !audience.isEmpty();
        this.strictIssuerAudience = hasIssuer || hasAudience || rs256PublicKey != null;
        this.issuerAudiencePolicyReady = !strictIssuerAudience
            || (hasIssuer && hasAudience && !issuer.isBlank() && !audience.isBlank()
                && issuer.equals(issuer.trim()) && audience.equals(audience.trim())
                && issuer.length() <= 255 && audience.length() <= 128);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = routePath(request);
        return !(path.equals(PREFIX) || path.startsWith(PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Misconfigured services remain healthy for diagnostics but no user or
        // audit data is ever exposed without a valid signing key.
        if (key == null || !issuerAudiencePolicyReady) {
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
            var signed = rs256Verifier.configured()
                ? rs256Verifier.verify(token)
                : Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            if (rs256PublicKey != null && !"RS256".equals(signed.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("Expected RS256 signature");
            }
            claims = signed.getPayload();
            // Do not trust token-scoped claims until the signature is verified.
            // The audience must be the SINGLE backend intended recipient; a
            // multi-audience token is not an authorization shortcut.
            if (strictIssuerAudience &&
                (!expectedIssuer.equals(claims.getIssuer())
                || claims.getAudience() == null || claims.getAudience().size() != 1
                || !claims.getAudience().contains(expectedAudience))) {
                throw new IllegalArgumentException("Signed token issuer or audience mismatch");
            }
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
