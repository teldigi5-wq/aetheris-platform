package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void validScopedTokenPassesAndVerifiedClaimsOverwriteSpoofedIdentityHeaders() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        String token = token(List.of("users:read"), Instant.now().plusSeconds(300));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Aetheris-User-Id", "999")
                        .header("X-Aetheris-User-Email", "attacker@example.com")
                        .header("X-Aetheris-User-Role", "ADMIN")
                        .header("X-Aetheris-Scopes", "users:write")
                        .build());

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = authenticated -> {
            forwarded.set(authenticated);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerWebExchange authenticated = forwarded.get();
        assertNotNull(authenticated);
        assertEquals("42", authenticated.getRequest().getHeaders().getFirst("X-Aetheris-User-Id"));
        assertEquals("user@example.com", authenticated.getRequest().getHeaders().getFirst("X-Aetheris-User-Email"));
        assertEquals("API_CONSUMER", authenticated.getRequest().getHeaders().getFirst("X-Aetheris-User-Role"));
        assertEquals("users:read", authenticated.getRequest().getHeaders().getFirst("X-Aetheris-Scopes"));
    }

    @Test
    void expiredTokenIsRejected() {
        assertRejected("Bearer " + token(List.of("users:read"), Instant.now().minusSeconds(30)), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedTokenIsRejected() {
        assertRejected("Bearer definitely-not-a-jwt", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingRequiredScopeIsForbidden() {
        assertRejected("Bearer " + token(List.of("orchestrator:read"), Instant.now().plusSeconds(300)), HttpStatus.FORBIDDEN);
    }

    @Test
    void protectedRouteRequiresBearerToken() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, authenticated -> {
            forwarded.set(authenticated);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void unprotectedRouteBypassesJwtRequirement() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, authenticated -> {
            forwarded.set(authenticated);
            return Mono.empty();
        }).block();

        assertNotNull(forwarded.get());
    }

    @Test
    void weakSigningSecretFailsClosedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new JwtAuthenticationFilter("too-short"));
    }


    @Test
    void memoryOwnerHeaderIsOverwrittenFromVerifiedNumericUid() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orchestrator/memory/inspector")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(List.of("orchestrator:read"), Instant.now().plusSeconds(300)))
                        .header("X-Aetheris-Owner-Id", "another-users-memory")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
        assertNotNull(forwarded.get());
        assertEquals("user:42", forwarded.get().getRequest().getHeaders().getFirst("X-Aetheris-Owner-Id"));
    }

    @Test
    void ownerHeaderIsStrippedOutsideMemoryEndpoints() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orchestrator/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(List.of("orchestrator:read"), Instant.now().plusSeconds(300)))
                        .header("X-Aetheris-Owner-Id", "forged")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Aetheris-Owner-Id"));
    }

    @Test
    void missingUidFailsClosedForMemoryEndpointsEvenWithForgedOwnerHeader() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        String missingUid = Jwts.builder()
                .subject("user@example.com")
                .claim("role", "API_CONSUMER")
                .claim("scopes", List.of("orchestrator:read"))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orchestrator/memory/search?q=private")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + missingUid)
                        .header("X-Aetheris-Owner-Id", "owner-victim")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void verifiedUserIdsProduceDifferentMemoryNamespaces() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        String secondToken = Jwts.builder()
                .subject("other@example.com")
                .claim("role", "API_CONSUMER")
                .claim("uid", 43L)
                .claim("scopes", List.of("orchestrator:read"))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orchestrator/memory/records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
        assertNotNull(forwarded.get());
        assertEquals("user:43", forwarded.get().getRequest().getHeaders().getFirst("X-Aetheris-Owner-Id"));
        assertNotEquals("user:42", forwarded.get().getRequest().getHeaders().getFirst("X-Aetheris-Owner-Id"));
    }

    private static void assertRejected(String authorization, HttpStatus expectedStatus) {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, authenticated -> {
            forwarded.set(authenticated);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get());
        assertEquals(expectedStatus, exchange.getResponse().getStatusCode());
    }

    private static String token(List<String> scopes, Instant expiration) {
        return Jwts.builder()
                .subject("user@example.com")
                .claim("role", "API_CONSUMER")
                .claim("uid", 42L)
                .claim("scopes", scopes)
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
