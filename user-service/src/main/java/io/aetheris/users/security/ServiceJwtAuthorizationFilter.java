package io.aetheris.users.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Independent service-side enforcement. No X-Aetheris-* header is trusted.
 * The current User/Audit APIs are global administrative APIs, NOT tenant APIs.
 * Only an identity-issued ADMIN bearer with explicit scope may reach them.
 * Future routes default to protected rather than inheriting a bypass.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ServiceJwtAuthorizationFilter extends OncePerRequestFilter {
    static final String DEFAULT_ISSUER = "aetheris-identity";
    static final String DEFAULT_AUDIENCE = "aetheris-api";
    public static final String VERIFIED_TENANT_ATTRIBUTE = "aetheris.user.signedTenant";
    private static final String UNSAFE_LOCAL_KEY = "change-me-local-development-secret-32chars";
    private final SecretKey signingKey;
    private final PublicKey verificationPublicKey;
    private final String keyId;
    private final String issuer;
    private final String audience;
    private final GatewayMutualTlsWorkloadPolicy gatewayWorkloadPolicy;

    @Autowired
    public ServiceJwtAuthorizationFilter(
            @Value("${aetheris.jwt.secret}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience,
            @Value("${aetheris.jwt.algorithm:HS256}") String algorithm,
            @Value("${aetheris.jwt.rsa-public-key-pem:}") String publicKeyPem,
            @Value("${aetheris.jwt.key-id:}") String keyId,
            @Value("${aetheris.gateway.workload-mtls-required:false}") String requiredMtlsText,
            @Value("${aetheris.gateway.workload-spiffe-id:}") String expectedSpiffeId,
            @Value("${server.ssl.enabled:false}") boolean tlsEnabled,
            @Value("${server.ssl.client-auth:}") String clientAuth,
            @Value("${server.ssl.trust-store:}") String trustStore,
            @Value("${server.ssl.bundle:}") String sslBundle) {
        if (!"true".equals(requiredMtlsText) && !"false".equals(requiredMtlsText)) {
            throw new IllegalArgumentException("Gateway workload mTLS opt-in must be true or false");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience are required");
        }
        if ("RS256".equals(algorithm)) {
            if (secret == null || !secret.isEmpty() || !JwtRs256KeySupport.validKid(keyId)) {
                throw new IllegalArgumentException("RS256 backend requires pinned public key without HMAC");
            }
            this.verificationPublicKey = JwtRs256KeySupport.publicKey(publicKeyPem);
            this.keyId = keyId;
            this.signingKey = null;
        } else if ("HS256".equals(algorithm)) {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 ||
                    UNSAFE_LOCAL_KEY.equals(secret) ||
                    (publicKeyPem != null && !publicKeyPem.isBlank()) ||
                    (keyId != null && !keyId.isBlank())) {
                throw new IllegalArgumentException("Invalid HS256 resource server configuration");
            }
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.verificationPublicKey = null;
            this.keyId = null;
        } else {
            throw new IllegalArgumentException("Unsupported JWT backend verifier algorithm");
        }
        this.issuer = issuer;
        this.audience = audience;
        boolean requiredMtls = "true".equals(requiredMtlsText);
        this.gatewayWorkloadPolicy = new GatewayMutualTlsWorkloadPolicy(
                requiredMtls, expectedSpiffeId, tlsEnabled, clientAuth, trustStore, sslBundle);
        if (requiredMtls && this.verificationPublicKey == null) {
            throw new IllegalArgumentException("Gateway mTLS requires pinned RS256 public-key verification");
        }
    }

    /** Legacy HS256 and RSA-only tests remain explicitly opt-out for mTLS. */
    public ServiceJwtAuthorizationFilter(String secret, String issuer, String audience,
            String algorithm, String publicKeyPem, String keyId) {
        this(secret, issuer, audience, algorithm, publicKeyPem, keyId,
                "false", "", false, "", "", "");
    }

    /** Synthetic-only constructor; runtime Spring creates the annotated constructor. */
    public ServiceJwtAuthorizationFilter(String secret) {
        this(secret, DEFAULT_ISSUER, DEFAULT_AUDIENCE, "HS256", "", "");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Only liveness and the servlet error dispatcher are publicly reachable.
        return path.equals("/actuator/health") ||
                path.equals("/actuator/health/liveness") ||
                path.equals("/actuator/health/readiness") ||
                request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Servlet certificate attribute must originate from a TLS-authenticated
        // connector; untrusted HTTP headers NEVER confer Gateway workload identity.
        if (!gatewayWorkloadPolicy.permits(request)) {
            deny(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (headers == null || !headers.hasMoreElements()) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String authorization = headers.nextElement();
        if (headers.hasMoreElements() || authorization == null ||
                !authorization.startsWith("Bearer ") ||
                authorization.length() <= 7 || authorization.length() > 8199) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        final Claims claims;
        try {
            io.jsonwebtoken.Jws<Claims> signed = verificationPublicKey != null ?
                    Jwts.parser().verifyWith(verificationPublicKey)
                            .requireIssuer(issuer).requireAudience(audience)
                            .build().parseSignedClaims(authorization.substring(7)) :
                    Jwts.parser().verifyWith(signingKey)
                            .requireIssuer(issuer).requireAudience(audience)
                            .build().parseSignedClaims(authorization.substring(7));
            if (verificationPublicKey != null ?
                    (!"RS256".equals(signed.getHeader().getAlgorithm()) ||
                     !keyId.equals(signed.getHeader().getKeyId())) :
                    (!"HS256".equals(signed.getHeader().getAlgorithm()) ||
                     signed.getHeader().getKeyId() != null)) {
                deny(response, HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            claims = signed.getPayload();
            Date issued = claims.getIssuedAt();
            Date expires = claims.getExpiration();
            Instant now = Instant.now();
            if (issued == null || expires == null ||
                    issued.toInstant().isAfter(now.plusSeconds(30)) ||
                    !expires.after(issued) ||
                    expires.toInstant().isAfter(issued.toInstant().plusSeconds(86400)) ||
                    claims.getSubject() == null || claims.getSubject().isBlank() ||
                    !(claims.get("uid") instanceof Number uid) ||
                    uid.longValue() <= 0 || uid.doubleValue() != uid.longValue()) {
                deny(response, HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } catch (Exception invalid) {
            // Deliberately do not log bearer tokens, identity claims or secrets.
            deny(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean tenantPath = path.equals("/api/users/tenant") ||
                path.startsWith("/api/users/tenant/");
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        String needed = tenantPath ? (read ? "tenant:users:read" : "tenant:users:write") :
                (read ? "users:read" : "users:write");
        Object scopes = claims.get("scopes");
        String role = claims.get("role", String.class);
        if (!(scopes instanceof Collection<?> values) || !values.contains(needed) ||
                !(tenantPath ? "API_CONSUMER".equals(role) : "ADMIN".equals(role))) {
            deny(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (tenantPath) {
            Object rawTid = claims.get("tid");
            if (!(rawTid instanceof String textTid)) {
                deny(response, HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            final UUID verifiedTid;
            try {
                verifiedTid = UUID.fromString(textTid);
                if (!verifiedTid.toString().equals(textTid)) {
                    deny(response, HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } catch (IllegalArgumentException badTenant) {
                deny(response, HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            // A Servlet request attribute is set *after* signature and scope
            // verification. Header/query/body tenant IDs are never authoritative.
            request.setAttribute(VERIFIED_TENANT_ATTRIBUTE, verifiedTid);
        }
        // Authorization comes from the cryptographically verified bearer only.
        // Forwarded role/user headers are never treated as evidence.
        chain.doFilter(request, response);
    }

    private static void deny(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write(status == HttpServletResponse.SC_UNAUTHORIZED ?
                "{\"status\":401,\"error\":\"Unauthorized\"}" :
                "{\"status\":403,\"error\":\"Forbidden\"}");
    }
}
