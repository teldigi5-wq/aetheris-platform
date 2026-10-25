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
        assertEquals("ADMIN", authenticated.getRequest().getHeaders().getFirst("X-Aetheris-User-Role"));
        assertEquals("users:read", authenticated.getRequest().getHeaders().getFirst("X-Aetheris-Scopes"));
        assertEquals("42", authenticated.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
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
        assertNull(exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        assertEquals(expectedStatus, exchange.getResponse().getStatusCode());
    }

    @Test
    void duplicateAuthorizationHeadersAreRejectedBeforeAnyBackendRouting() {
        String bearer = "Bearer " + token(List.of("users:read"), Instant.now().plusSeconds(120));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, bearer, bearer).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void directAdministrativeRolePolicyMatchesUsersAndAuditOnEveryRead() {
        for (String path : List.of("/api/users", "/api/users/7",
                                   "/api/events", "/api/events/7")) {
            String required = path.startsWith("/api/users") ? "users:read" : "services:read";
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                        tokenWithClaims("API_CONSUMER", 42L, List.of(required),
                            Instant.now().minusSeconds(5), Instant.now().plusSeconds(120)))
                    .build());
            AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
            filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
            assertNull(forwarded.get());
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void administrativeRoutesRefuseFractionalNonpositiveOrMissingUserIdentity() {
        for (Object uid : List.of(42.5d, 0, -1L)) {
            String token = tokenWithClaims("ADMIN", uid, List.of("users:read"),
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(120));
            assertRejected("Bearer " + token, HttpStatus.UNAUTHORIZED);
        }
        assertRejected("Bearer " + tokenWithClaims("ADMIN", null, List.of("users:read"),
            Instant.now().minusSeconds(5), Instant.now().plusSeconds(120)), HttpStatus.UNAUTHORIZED);
        assertRejected("Bearer " + tokenWithClaims("ADMIN", 42L, List.of("users:read"),
            null, Instant.now().plusSeconds(120)), HttpStatus.UNAUTHORIZED);
        assertRejected("Bearer " + tokenWithClaims("ADMIN", 42L, List.of("users:read"),
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void signedDeveloperOrApiConsumerStillAccessesOrchestratorReadOnly() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orchestrator")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                    tokenWithClaims("API_CONSUMER", 42L, List.of("orchestrator:read"),
                        Instant.now().minusSeconds(5), Instant.now().plusSeconds(120)))
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
        assertNotNull(forwarded.get());
        assertEquals("API_CONSUMER",
            forwarded.get().getRequest().getHeaders().getFirst("X-Aetheris-User-Role"));
        assertEquals("42", forwarded.get().getAttribute(
            JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
    }

    @Test
    void invalidOrchestratorUidCannotPoisonVerifiedRateLimitIdentity() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orchestrator")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                    tokenWithClaims("API_CONSUMER", 42.5d, List.of("orchestrator:read"),
                        Instant.now().minusSeconds(5), Instant.now().plusSeconds(120)))
                .header("X-Aetheris-User-Id", "333")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, auth -> { forwarded.set(auth); return Mono.empty(); }).block();
        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Aetheris-User-Id"));
    }

    private static String tokenWithClaims(String role, Object uid, List<String> scopes,
                                          Instant issued, Instant expires) {
        var builder = Jwts.builder()
            .subject("user@example.com")
            .claim("role", role)
            .claim("uid", uid)
            .claim("scopes", scopes)
            .expiration(Date.from(expires));
        if (issued != null) {
            builder.issuedAt(Date.from(issued));
        }
        return builder.signWith(Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static String token(List<String> scopes, Instant expiration) {
        return Jwts.builder()
                .subject("user@example.com")
                .claim("role", "ADMIN")
                .claim("uid", 42L)
                .claim("scopes", scopes)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
