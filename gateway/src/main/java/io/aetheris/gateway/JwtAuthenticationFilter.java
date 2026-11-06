package io.aetheris.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    static final String VERIFIED_USER_ID_ATTRIBUTE = "aetheris.gateway.verifiedUserId";
    private static final Set<HttpMethod> WRITE_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    public static final String DEFAULT_ISSUER = "aetheris-identity";
    public static final String DEFAULT_AUDIENCE = "aetheris-api";
    private final SecretKey key;
    private final PublicKey verificationPublicKey;
    private final String keyId;
    private final String issuer;
    private final String audience;

    @Autowired
    public JwtAuthenticationFilter(
            @Value("${aetheris.jwt.secret}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience,
            @Value("${aetheris.jwt.algorithm:HS256}") String algorithm,
            @Value("${aetheris.jwt.rsa-public-key-pem:}") String publicKeyPem,
            @Value("${aetheris.jwt.key-id:}") String keyId) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
        if ("RS256".equals(algorithm)) {
            if (secret == null || !secret.isEmpty() || !JwtRs256KeySupport.validKid(keyId)) {
                throw new IllegalArgumentException("RS256 verifier must have pinned public key only");
            }
            this.verificationPublicKey = JwtRs256KeySupport.publicKey(publicKeyPem);
            this.keyId = keyId;
            this.key = null;
        } else if ("HS256".equals(algorithm)) {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 ||
                    "change-me-local-development-secret-32chars".equals(secret) ||
                    (publicKeyPem != null && !publicKeyPem.isBlank()) ||
                    (keyId != null && !keyId.isBlank())) {
                throw new IllegalArgumentException("HS256 verifier configuration invalid");
            }
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.verificationPublicKey = null;
            this.keyId = null;
        } else {
            throw new IllegalArgumentException("Unsupported JWT verifier algorithm");
        }
        this.issuer = issuer;
        this.audience = audience;
    }

    // Source-compatible deterministic constructor for existing gateway unit tests.
    public JwtAuthenticationFilter(String secret) {
        this(secret, DEFAULT_ISSUER, DEFAULT_AUDIENCE, "HS256", "", "");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!isProtectedRoute(path)) {
            return chain.filter(exchange);
        }

        java.util.List<String> authorizations = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        String authorization = authorizations != null && authorizations.size() == 1 ?
                authorizations.get(0) : null;
        if (authorization == null || !authorization.startsWith("Bearer ") ||
                authorization.length() <= 7 || authorization.length() > 8199 ||
                !authorization.substring(7).equals(authorization.substring(7).trim())) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Missing bearer token");
        }

        String token = authorization.substring(7).trim();
        try {
            io.jsonwebtoken.Jws<Claims> verified = verificationPublicKey != null ?
                    Jwts.parser().verifyWith(verificationPublicKey)
                            .requireIssuer(issuer).requireAudience(audience)
                            .build().parseSignedClaims(token) :
                    Jwts.parser().verifyWith(key)
                            .requireIssuer(issuer).requireAudience(audience)
                            .build().parseSignedClaims(token);
            if (verificationPublicKey != null ?
                    (!"RS256".equals(verified.getHeader().getAlgorithm()) ||
                     !keyId.equals(verified.getHeader().getKeyId())) :
                    (!"HS256".equals(verified.getHeader().getAlgorithm()) ||
                     verified.getHeader().getKeyId() != null)) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid access token algorithm");
            }
            Claims claims = verified.getPayload();

            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            Object userId = claims.get("uid");
            Set<String> scopes = readScopes(claims.get("scopes"));

            if (email == null || email.isBlank() || role == null ||
                    !(userId instanceof Number accountId) || accountId.longValue() <= 0L) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Token is missing required claims");
            }

            // New tenant endpoints are separate from legacy ADMIN-only global APIs.
            // The tenant comes ONLY from a valid Identity-signed JWT claim.
            boolean tenantPath = isTenantUserPath(path);
            if (tenantPath) {
                if (!"API_CONSUMER".equals(role)) {
                    return error(exchange, HttpStatus.FORBIDDEN, "Forbidden", "Tenant account role required");
                }
                Object rawTid = claims.get("tid");
                if (!(rawTid instanceof String textTid) || !validTenantId(textTid)) {
                    return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Tenant identity required");
                }
            } else if ((path.equals("/api/users") || path.startsWith("/api/users/") ||
                    path.equals("/api/events") || path.startsWith("/api/events/")) &&
                    !"ADMIN".equals(role)) {
                return error(exchange, HttpStatus.FORBIDDEN, "Forbidden", "Administrator role required");
            }

            String requiredScope = requiredScope(path, exchange.getRequest().getMethod());
            if (requiredScope != null && !scopes.contains(requiredScope)) {
                return error(exchange, HttpStatus.FORBIDDEN, "Forbidden", "Missing required scope: " + requiredScope);
            }

            // The rate limiter must only use a server-side, cryptographically
            // verified identifier. Client X-Aetheris-User-Id headers are untrusted.
            exchange.getAttributes().put(VERIFIED_USER_ID_ATTRIBUTE,
                    Long.toString(((Number) userId).longValue()));

            ServerWebExchange authenticatedExchange = exchange.mutate()
                    .request(builder -> builder.headers(headers -> {
                        headers.remove("X-Aetheris-User-Id");
                        headers.remove("X-Aetheris-User-Email");
                        headers.remove("X-Aetheris-User-Role");
                        headers.remove("X-Aetheris-Scopes");
                        headers.remove("X-Aetheris-Tenant-Id");
                        headers.set("X-Aetheris-User-Email", email);
                        headers.set("X-Aetheris-User-Role", role);
                        headers.set("X-Aetheris-Scopes", String.join(" ", scopes.stream().sorted().toList()));
                        headers.set("X-Aetheris-User-Id", String.valueOf(((Number) userId).longValue()));
                    }))
                    .build();

            return chain.filter(authenticatedExchange);
        } catch (Exception exception) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired access token");
        }
    }

    private static boolean isTenantUserPath(String path) {
        return path.equals("/api/users/tenant") ||
                path.startsWith("/api/users/tenant/");
    }

    private static boolean validTenantId(String tid) {
        try {
            UUID parsed = UUID.fromString(tid);
            return parsed.toString().equals(tid);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private Set<String> readScopes(Object claim) {
        Set<String> scopes = new HashSet<>();
        if (claim instanceof Collection<?> values) {
            values.stream().filter(String.class::isInstance).map(String.class::cast).forEach(scopes::add);
        } else if (claim instanceof String value) {
            for (String scope : value.split("\\s+")) {
                if (!scope.isBlank()) scopes.add(scope);
            }
        }
        return scopes;
    }

    private boolean isProtectedRoute(String path) {
        return path.equals("/api/users") || path.startsWith("/api/users/")
                || path.equals("/api/events") || path.startsWith("/api/events/")
                || path.equals("/api/orchestrator") || path.startsWith("/api/orchestrator/");
    }

    private String requiredScope(String path, HttpMethod method) {
        if (isTenantUserPath(path)) {
            return method != null && WRITE_METHODS.contains(method)
                    ? "tenant:users:write" : "tenant:users:read";
        }
        if (path.equals("/api/events") || path.startsWith("/api/events/")) {
            return "services:read";
        }
        if (path.equals("/api/orchestrator") || path.startsWith("/api/orchestrator/")) {
            return method != null && WRITE_METHODS.contains(method) ? "orchestrator:write" : "orchestrator:read";
        }
        return method != null && WRITE_METHODS.contains(method) ? "users:write" : "users:read";
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
        byte[] bytes = ("{\"status\":" + status.value() + ",\"error\":\"" + error + "\",\"message\":\"" + escapedMessage + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
