package io.aetheris.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    private static final Set<HttpMethod> WRITE_METHODS = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private final SecretKey key;

    public JwtAuthenticationFilter(@Value("${aetheris.jwt.secret}") String secret) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Routed paths reachable without a bearer token. Everything else routed through the gateway requires one. */
    static final List<String> PUBLIC_ROUTE_PREFIXES = List.of("/api/auth/");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (isAmbiguousPath(rawPath)) {
            return error(exchange, HttpStatus.BAD_REQUEST, "Bad Request", "Ambiguous request path");
        }
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicRoute(path)) {
            return chain.filter(exchange);
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

            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            Object userId = claims.get("uid");
            Set<String> scopes = readScopes(claims.get("scopes"));

            if (email == null || role == null) {
                return error(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Token is missing required claims");
            }

            String requiredScope = requiredScope(path, exchange.getRequest().getMethod());
            if (requiredScope == null) {
                // Deny by default: a route without an explicit scope rule is never forwarded.
                return error(exchange, HttpStatus.FORBIDDEN, "Forbidden", "No access policy for route");
            }
            if (!scopes.contains(requiredScope)) {
                return error(exchange, HttpStatus.FORBIDDEN, "Forbidden", "Missing required scope: " + requiredScope);
            }

            ServerWebExchange authenticatedExchange = exchange.mutate()
                    .request(builder -> builder.headers(headers -> {
                        headers.remove("X-Aetheris-User-Id");
                        headers.remove("X-Aetheris-User-Email");
                        headers.remove("X-Aetheris-User-Role");
                        headers.remove("X-Aetheris-Scopes");
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

    static boolean isPublicRoute(String path) {
        return PUBLIC_ROUTE_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * Rejects paths that different layers could interpret differently (dot segments, empty
     * segments, matrix parameters, encoded separators or dots, backslashes), so a request
     * cannot look public to this filter while a route predicate or backend resolves it elsewhere.
     */
    static boolean isAmbiguousPath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return false;
        }
        String lower = rawPath.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("//") || lower.contains("/./") || lower.contains("/../")
                || lower.endsWith("/.") || lower.endsWith("/..") || lower.contains(";")
                || lower.contains("\\") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%2e") || lower.contains("%3b") || lower.contains("%25");
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    /** Explicit scope rule per protected prefix; null means no policy and the request is refused. */
    static String requiredScope(String path, HttpMethod method) {
        boolean write = method != null && WRITE_METHODS.contains(method);
        if (under(path, "/api/users")) {
            return write ? "users:write" : "users:read";
        }
        if (under(path, "/api/events")) {
            return "services:read";
        }
        if (under(path, "/api/orchestrator")) {
            return write ? "orchestrator:write" : "orchestrator:read";
        }
        return null;
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
