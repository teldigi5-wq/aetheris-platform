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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Defense in depth for routes reachable without the API Gateway.
 * Verifies the ORIGINAL signed client bearer JWT independently, never forwarded
 * X-Aetheris-* headers, pod IP, localhost access, or a synthetic service header.
 * Administrative APIs are single-domain, not tenant-aware: until a real
 * owner/tenant model exists, they are ADMIN-only.
 *
 * Signed issuer/audience claims are independently verified in this draft.
 * Shared-key rotation, revocation and workload identity remain open (#251 / #229).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class DirectApiJwtFilter extends OncePerRequestFilter {
    public static final String DEFAULT_ISSUER = "aetheris-identity";
    public static final String DEFAULT_AUDIENCE = "aetheris-api";
    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private static final String ROOT = "/api/users";

    @Autowired
    public DirectApiJwtFilter(@Value("${aetheris.jwt.secret}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Backend JWT secret must be at least 32 UTF-8 bytes");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Existing MockMvc constructor; enforces the same strict defaults as production DI.
    public DirectApiJwtFilter(String secret) {
        this(secret, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // URI minus the container context path works for standalone MockMvc, root
        // Servlet mappings, and non-root deployments. Never trust forwarded URI headers.
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = uri.startsWith(context) ? uri.substring(context.length()) : uri;
        return !(ROOT.equals(path) || path.startsWith(ROOT + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        List<String> headers = java.util.Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.size() != 1 || headers.get(0) == null
                || headers.get(0).length() > 4096 || !headers.get(0).startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer authorization required");
            return;
        }

        final Claims claims;
        try {
            String token = headers.get(0).substring("Bearer ".length());
            if (token.isBlank() || token.contains(" ") || token.contains("\t")) {
                throw new IllegalArgumentException("Malformed bearer token");
            }
            claims = Jwts.parser().verifyWith(key).requireIssuer(issuer)
                .requireAudience(audience).build().parseSignedClaims(token).getPayload();
            Date expires = claims.getExpiration();
            Date issued = claims.getIssuedAt();
            String subject = claims.getSubject();
            Object uid = claims.get("uid");
            if (expires == null || !expires.toInstant().isAfter(Instant.now())
                || issued == null || issued.toInstant().isAfter(Instant.now().plusSeconds(30))
                || subject == null || subject.isBlank()
                || !isPositiveIntegralUid(uid)) {
                throw new IllegalArgumentException("Required verified JWT claims missing");
            }
        } catch (Exception exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
            return;
        }

        Object role = claims.get("role");
        Object scopes = claims.get("scopes");
        boolean administrator = Objects.equals(role, "ADMIN");
        boolean authorizedScope = scopes instanceof Collection<?> collection
            && collection.stream().allMatch(String.class::isInstance)
            && collection.contains(requiredScope(request));
        if (!administrator || !authorizedScope) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permission");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * JSON numbers are not interchangeable identities. Parsing uid=42.5 by
     * longValue() silently authenticates it as 42; very large numbers can
     * overflow. The Identity issuer writes a Java Long, decoded as Integer
     * for small JSON integers and Long for large ones.
     */
    private static boolean isPositiveIntegralUid(Object uid) {
        return (uid instanceof Integer integerId && integerId > 0)
            || (uid instanceof Long longId && longId > 0L);
    }

    private String requiredScope(HttpServletRequest request) {
        return ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
            ? "users:read" : "users:write";
    }
}
