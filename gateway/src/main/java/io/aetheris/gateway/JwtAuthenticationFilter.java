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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final Set<HttpMethod> WRITE_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private final SecretKey key;
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
        @Value("${AETHERIS_GATEWAY_JWT_ORCHESTRATOR_AUDIENCE:}") String orchestratorAudience) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = issuer;
        this.usersAudience = usersAudience;
        this.auditAudience = auditAudience;
        this.orchestratorAudience = orchestratorAudience;
        // A partially set policy must NOT revert to legacy authorization.
        this.strictTargetedMode = nonEmpty(issuer) || nonEmpty(usersAudience)
            || nonEmpty(auditAudience) || nonEmpty(orchestratorAudience);
        this.issuerConfigurationReady = !strictTargetedMode || valid(issuer, 255);
    }

    // Only existing tests and deliberate direct construction use legacy mode.
    JwtAuthenticationFilter(String secret) {
        this(secret, "", "", "", "");
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
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Missing bearer token");
        }

        String token = authorization.substring(7).trim();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

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
            Set<String> scopes = readScopes(claims.get("scopes"));

            if (email == null || role == null) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Token is missing required claims");
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
