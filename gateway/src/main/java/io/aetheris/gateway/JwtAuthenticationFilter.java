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
import java.time.Instant;
import java.util.Map;
import java.util.Collection;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final Set<HttpMethod> WRITE_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private static final Map<String, Set<String>> ROLE_SCOPES = Map.of(
        "ADMIN", Set.of("users:read", "users:write", "services:read",
            "identity:read", "identity:write", "orchestrator:read", "orchestrator:write"),
        "DEVELOPER", Set.of("users:read", "users:write", "services:read",
            "identity:read", "orchestrator:read", "orchestrator:write"),
        "API_CONSUMER", Set.of("users:read", "services:read", "orchestrator:read")
    );
    private final SecretKey key;
    private final PublicKey rs256PublicKey;
    private final String expectedIssuer;
    private final String usersAudience;
    private final String auditAudience;
    private final String orchestratorAudience;
    private final boolean strictTargetedMode;
    private final boolean issuerConfigurationReady;

    @Autowired
    public JwtAuthenticationFilter(
        @Value("${aetheris.jwt.secret}") String secret,
        @Value("${AETHERIS_GATEWAY_JWT_ISSUER:}") String issuer,
        @Value("${AETHERIS_GATEWAY_JWT_USERS_AUDIENCE:}") String usersAudience,
        @Value("${AETHERIS_GATEWAY_JWT_AUDIT_AUDIENCE:}") String auditAudience,
        @Value("${AETHERIS_GATEWAY_JWT_ORCHESTRATOR_AUDIENCE:}") String orchestratorAudience,
        @Value("${AETHERIS_GATEWAY_JWT_RS256_PUBLIC_KEY_PEM:}") String rs256Pem) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // Public key verification never attempts legacy HMAC fallback.
        this.rs256PublicKey = rs256Pem == null || rs256Pem.isEmpty()
            ? null : JwtRs256KeySupport.publicKey(rs256Pem);
        this.expectedIssuer = issuer;
        this.usersAudience = usersAudience;
        this.auditAudience = auditAudience;
        this.orchestratorAudience = orchestratorAudience;
        // A partially set policy must NOT revert to legacy authorization.
        this.strictTargetedMode = rs256PublicKey != null || nonEmpty(issuer) || nonEmpty(usersAudience)
            || nonEmpty(auditAudience) || nonEmpty(orchestratorAudience);
        this.issuerConfigurationReady = !strictTargetedMode || valid(issuer, 255);
    }

    // Only existing tests and deliberate direct construction use legacy mode.
    JwtAuthenticationFilter(String secret) {
        this(secret, "", "", "", "", "");
    }

    JwtAuthenticationFilter(String secret, String issuer, String users,
                            String audit, String orchestrator) {
        this(secret, issuer, users, audit, orchestrator, "");
    }

    private static boolean nonEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean valid(String value, int limit) {
        return value != null && !value.isBlank()
            && value.length() <= limit && value.equals(value.trim());
    }

    private String audienceForPath(String path) {
        if (path.equals("/api/users") || path.startsWith("/api/users/")) return usersAudience;
        if (path.equals("/api/events") || path.startsWith("/api/events/")) return auditAudience;
        if (path.equals("/api/orchestrator") || path.startsWith("/api/orchestrator/"))
            return orchestratorAudience;
        return null;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!isProtectedRoute(path)) {
            return chain.filter(exchange);
        }

        final String expectedAudience = audienceForPath(path);
        if (strictTargetedMode && (!issuerConfigurationReady || !valid(expectedAudience, 128))) {
            // Missing per-route recipient blocks that route; no fallback to
            // accepting a token for some other service (confused deputy).
            return error(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Unavailable",
                    "JWT target policy is incomplete");
        }
        if (exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).size() != 1) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Exactly one bearer authorization header required");
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.length() > 8192
                || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Missing bearer token");
        }

        String token = authorization.substring(7).trim();
        try {
            var signed = (rs256PublicKey == null
                    ? Jwts.parser().verifyWith(key)
                    : Jwts.parser().verifyWith(rs256PublicKey))
                    .build().parseSignedClaims(token);
            if (rs256PublicKey != null && !"RS256".equals(signed.getHeader().getAlgorithm())) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "Expected RS256 signature");
            }
            Claims claims = signed.getPayload();

            if (strictTargetedMode && (
                !expectedIssuer.equals(claims.getIssuer())
                || claims.getAudience() == null || claims.getAudience().size() != 1
                || !claims.getAudience().contains(expectedAudience))) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "Signed issuer or audience does not match this route");
            }

            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            Object userId = claims.get("uid");
            Set<String> scopes = verifiedScopes(claims.get("scopes"));
            Set<String> allowedByRole = ROLE_SCOPES.get(role);
            Instant issuedAt = claims.getIssuedAt() == null
                ? null : claims.getIssuedAt().toInstant();
            Instant expires = claims.getExpiration() == null
                ? null : claims.getExpiration().toInstant();

            // Match the direct-backend resource boundary before asserting
            // an authenticated principal in forwarded X-Aetheris headers.
            if (email == null || email.isBlank() || email.length() > 254
                || !email.equals(email.trim())
                || !(userId instanceof Long || userId instanceof Integer)
                || ((Number) userId).longValue() <= 0
                || allowedByRole == null || scopes.isEmpty()
                || !allowedByRole.containsAll(scopes)
                || issuedAt == null || expires == null
                || issuedAt.isAfter(Instant.now().plusSeconds(30))
                || !expires.isAfter(issuedAt)) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Invalid signed principal claims");
            }

            // Direct User/Audit controllers are privileged-only until tenant
            // and object ownership are proven. Never grant API_CONSUMER
            // access merely because legacy role scopes contain users:read.
            if ((path.equals("/api/users") || path.startsWith("/api/users/")
                    || path.equals("/api/events") || path.startsWith("/api/events/"))
                    && role.equals("API_CONSUMER")) {
                return error(exchange, HttpStatus.FORBIDDEN, "Forbidden",
                    "Direct backend access requires privileged role");
            }

            String requiredScope = requiredScope(path, exchange.getRequest().getMethod());
            if (requiredScope != null && !scopes.contains(requiredScope)) {
                return error(exchange, HttpStatus.FORBIDDEN, "Forbidden", "Missing required scope: " + requiredScope);
            }

            ServerWebExchange authenticatedExchange = exchange.mutate()
                    .request(builder -> builder.headers(headers -> {
                        headers.remove("X-Aetheris-User-Id");
                        headers.remove("X-Aetheris-User-Email");
                        headers.remove("X-Aetheris-User-Role");
                        headers.remove("X-Aetheris-Scopes");
                        // Never forward caller-supplied tenant evidence.
                        // No trusted tenant model exists in Identity yet.
                        headers.remove("X-Aetheris-Tenant-Id");
                        headers.set("X-Aetheris-User-Email", email);
                        headers.set("X-Aetheris-User-Role", role);
                        headers.set("X-Aetheris-Scopes", String.join(" ", scopes.stream().sorted().toList()));
                        if (userId != null) {
                            headers.set("X-Aetheris-User-Id", String.valueOf(userId));
                        }
                    }))
                    .build();

            return chain.filter(authenticatedExchange);
        } catch (Exception exception) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired access token");
        }
    }

    private static Set<String> verifiedScopes(Object claim) {
        if (!(claim instanceof Collection<?> values) || values.isEmpty()
                || values.size() > 32) return Set.of();
        Set<String> scopes = new java.util.HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String scope) || scope.isBlank()
                    || scope.length() > 80) return Set.of();
            scopes.add(scope);
        }
        return Set.copyOf(scopes);
    }

    private boolean isProtectedRoute(String path) {
        return path.equals("/api/users") || path.startsWith("/api/users/")
                || path.equals("/api/events") || path.startsWith("/api/events/")
                || path.equals("/api/orchestrator") || path.startsWith("/api/orchestrator/");
    }

    private String requiredScope(String path, HttpMethod method) {
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
