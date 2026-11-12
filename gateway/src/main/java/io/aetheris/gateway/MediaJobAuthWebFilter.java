package io.aetheris.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Dedicated WebFlux security boundary for locally handled /api/media endpoints.
 * Unlike Gateway GlobalFilters, WebFilters also run before annotated controllers.
 * Never authorizes a user using caller-provided X-Aetheris-* headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public final class MediaJobAuthWebFilter implements WebFilter {
    static final String VERIFIED_OWNER_ATTRIBUTE = "aetheris.media.verifiedOwnerUid";
    private final SecretKey key;

    public MediaJobAuthWebFilter(@Value("${aetheris.jwt.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path == null || !(path.equals("/api/media") || path.startsWith("/api/media/"))) {
            return chain.filter(exchange);
        }

        // This is a native-token-only endpoint. Browser cookies/Origin are not
        // an authentication mechanism, and cross-origin browser writes fail closed.
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        if (path.indexOf('%') >= 0 || path.contains("//") || path.contains("..")) {
            return refuse(exchange, HttpStatus.BAD_REQUEST, "Invalid media request path");
        }
        if (exchange.getRequest().getHeaders().containsKey(HttpHeaders.ORIGIN)
                || exchange.getRequest().getHeaders().containsKey(HttpHeaders.COOKIE)) {
            return refuse(exchange, HttpStatus.FORBIDDEN, "Media staging requires native bearer authentication");
        }
        if (!"syntra-native".equals(exchange.getRequest().getHeaders().getFirst("X-Aetheris-Client"))) {
            return refuse(exchange, HttpStatus.FORBIDDEN, "Unsupported media client");
        }

        HttpMethod method = exchange.getRequest().getMethod();
        if (!HttpMethod.GET.equals(method) && !HttpMethod.POST.equals(method)
                && !HttpMethod.DELETE.equals(method)) {
            return refuse(exchange, HttpStatus.METHOD_NOT_ALLOWED, "Unsupported media method");
        }
        if (HttpMethod.POST.equals(method)) {
            MediaType contentType = exchange.getRequest().getHeaders().getContentType();
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                    || exchange.getRequest().getHeaders().getContentLength() > 4096) {
                return refuse(exchange, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media staging accepts bounded JSON only");
            }
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ") || auth.length() < 20) {
            return refuse(exchange, HttpStatus.UNAUTHORIZED, "Missing media bearer token");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(auth.substring(7).trim()).getPayload();
            Object uid = claims.get("uid");
            if (!(uid instanceof Integer || uid instanceof Long)
                    || ((Number) uid).longValue() <= 0
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || !(claims.get("role") instanceof String role) || role.isBlank()) {
                return refuse(exchange, HttpStatus.UNAUTHORIZED, "Invalid media identity claims");
            }

            Set<String> scopes = readScopes(claims.get("scopes"));
            String requiredScope = HttpMethod.GET.equals(method) ? "media:read" : "media:write";
            if (!scopes.contains(requiredScope)) {
                return refuse(exchange, HttpStatus.FORBIDDEN, "Missing media scope");
            }

            exchange.getAttributes().put(VERIFIED_OWNER_ATTRIBUTE, ((Number) uid).longValue());
            return chain.filter(exchange);
        } catch (RuntimeException invalidToken) {
            return refuse(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired media token");
        }
    }

    private static Set<String> readScopes(Object raw) {
        Set<String> scopes = new HashSet<>();
        if (raw instanceof Collection<?> values) {
            values.stream().filter(String.class::isInstance).map(String.class::cast).forEach(scopes::add);
        } else if (raw instanceof String value) {
            for (String scope : value.split("\\s+")) {
                if (!scope.isBlank()) scopes.add(scope);
            }
        }
        return scopes;
    }

    private static Mono<Void> refuse(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer data = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(data));
    }
}
