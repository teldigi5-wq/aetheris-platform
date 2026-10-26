package io.aetheris.audit.security;

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
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
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
    private final PublicKey rsaPublicKey;
    private final String rsaKid;
    private final String mode;
    private final String issuer;
    private final String audience;
    private static final String ROOT = "/api/events";

    @Autowired
    public DirectApiJwtFilter(@Value("${aetheris.jwt.secret:}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience,
            @Value("${aetheris.jwt.mode:hmac}") String mode,
            @Value("${aetheris.jwt.rsa-public-key-x509-base64:}") String publicKey,
            @Value("${aetheris.jwt.kid:}") String kid) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.mode = mode;
        if ("hmac".equals(mode)) {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                    || (publicKey != null && !publicKey.isBlank())
                    || (kid != null && !kid.isBlank())) {
                throw new IllegalArgumentException("Invalid HMAC JWT verifier configuration");
            }
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.rsaPublicKey = null;
            this.rsaKid = null;
        } else if ("rs256".equals(mode)) {
            if ((secret != null && !secret.isBlank()) || publicKey == null
                    || publicKey.isBlank() || publicKey.length() > 16384
                    || kid == null || !kid.matches("[a-z][a-z0-9-]{0,30}")) {
                throw new IllegalArgumentException("Invalid RS256 JWT verifier configuration");
            }
            try {
                byte[] der = Base64.getDecoder().decode(publicKey);
                PublicKey parsed = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(der));
                if (!(parsed instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 3072) {
                    throw new IllegalArgumentException("RSA verifier key too weak");
                }
                this.rsaPublicKey = parsed;
            } catch (Exception invalidKey) {
                throw new IllegalArgumentException("Invalid RSA public verification key");
            }
            this.rsaKid = kid;
            this.key = null;
        } else {
            throw new IllegalArgumentException("Unsupported JWT verification mode");
        }
    }

    // Existing HMAC contracts are preserved until reviewed coordinated rollout.
    public DirectApiJwtFilter(String secret, String issuer, String audience) {
        this(secret, issuer, audience, "hmac", "", "");
    }

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
            var parser = "rs256".equals(mode)
                ? Jwts.parser().verifyWith(rsaPublicKey)
                : Jwts.parser().verifyWith(key);
            var signed = parser.requireIssuer(issuer).requireAudience(audience)
                .build().parseSignedClaims(token);
            if ("rs256".equals(mode) && (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !rsaKid.equals(signed.getHeader().getKeyId()))) {
                throw new SecurityException("Rejected JWT signing key identity");
            }
            claims = signed.getPayload();
            // requireAudience() checks membership; reject cross-workload multi-audience tokens.
            if (!java.util.Set.of(audience).equals(claims.getAudience())) {
                throw new IllegalArgumentException("Ambiguous bearer token audience");
            }
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
            ? "services:read" : "services:write";
    }
}
