package io.aetheris.audit.security;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;

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
    private static final String UNSAFE_LOCAL_KEY = "change-me-local-development-secret-32chars";
    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final boolean publicMetricsScrape;

    @Autowired
    public ServiceJwtAuthorizationFilter(
            @Value("${aetheris.jwt.secret}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience,
            @Value("${aetheris.metrics.public-scrape:false}") boolean publicMetricsScrape) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 ||
                UNSAFE_LOCAL_KEY.equals(secret)) {
            throw new IllegalArgumentException("A non-default JWT signing key of >=32 bytes is required");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience are required");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.publicMetricsScrape = publicMetricsScrape;
    }

    /** Metrics scrape stays protected unless explicitly opted in (local observability profile only). */
    public ServiceJwtAuthorizationFilter(String secret, String issuer, String audience) {
        this(secret, issuer, audience, false);
    }

    /** Synthetic-only constructor; runtime Spring creates the annotated constructor. */
    public ServiceJwtAuthorizationFilter(String secret) {
        this(secret, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Only liveness and the servlet error dispatcher are publicly reachable.
        return path.equals("/actuator/health") ||
                path.equals("/actuator/health/liveness") ||
                path.equals("/actuator/health/readiness") ||
                (publicMetricsScrape && path.equals("/actuator/prometheus")) ||
                request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
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
            var signed = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(authorization.substring(7));
            if (!"HS256".equals(signed.getHeader().getAlgorithm())) {
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

        String needed = "services:read";
        Object scopes = claims.get("scopes");
        if (!"ADMIN".equals(claims.get("role", String.class)) ||
                !(scopes instanceof Collection<?> values) ||
                !values.contains(needed)) {
            deny(response, HttpServletResponse.SC_FORBIDDEN);
            return;
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
