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
    void publicAuthRouteBypassesJwtRequirement() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").build());
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

    @Test
    void auditMutationNeverUsesReadScopeAtTheGateway() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        for (org.springframework.http.HttpMethod method : List.of(
                org.springframework.http.HttpMethod.POST,
                org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpMethod.PATCH,
                org.springframework.http.HttpMethod.DELETE)) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, "/api/events")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                        token(List.of("services:read"), Instant.now().plusSeconds(120))).build());
            AtomicReference<ServerWebExchange> routed = new AtomicReference<>();
            filter.filter(exchange, e -> { routed.set(e); return Mono.empty(); }).block();
            assertNull(routed.get(), method.toString());
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void auditReadStillPermitsAnAuthenticatedAdmin() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                    token(List.of("services:read"), Instant.now().plusSeconds(120))).build());
        AtomicReference<ServerWebExchange> next = new AtomicReference<>();
        filter.filter(exchange, e -> { next.set(e); return Mono.empty(); }).block();
        assertNotNull(next.get());
        assertEquals("ADMIN", next.get().getRequest().getHeaders().getFirst("X-Aetheris-User-Role"));
    }

    private static String tokenWithClaims(String role, Object uid, List<String> scopes,
                                          Instant issued, Instant expires) {
        var builder = Jwts.builder()
            .issuer(JwtAuthenticationFilter.DEFAULT_ISSUER)
            .audience().add(JwtAuthenticationFilter.DEFAULT_AUDIENCE).and()
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
                .issuer(JwtAuthenticationFilter.DEFAULT_ISSUER)
                .audience().add(JwtAuthenticationFilter.DEFAULT_AUDIENCE).and()
                .subject("user@example.com")
                .claim("role", "ADMIN")
                .claim("uid", 42L)
                .claim("scopes", scopes)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String tokenForIssuerAudience(String issuer, String audience) {
        var builder = Jwts.builder()
            .subject("issuer-boundary@example.invalid")
            .claim("uid", 42L)
            .claim("role", "ADMIN")
            .claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(Instant.now().minusSeconds(5)))
            .expiration(Date.from(Instant.now().plusSeconds(120)));
        if (issuer != null) builder.issuer(issuer);
        if (audience != null) builder.audience().add(audience).and();
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void gatewayRejectsSignedWrongOrAbsentIssuerAudienceWithoutRouting() {
        for (String jwt : List.of(
                tokenForIssuerAudience("wrong-issuer", JwtAuthenticationFilter.DEFAULT_AUDIENCE),
                tokenForIssuerAudience(JwtAuthenticationFilter.DEFAULT_ISSUER, "wrong-audience"),
                tokenForIssuerAudience(null, JwtAuthenticationFilter.DEFAULT_AUDIENCE),
                tokenForIssuerAudience(JwtAuthenticationFilter.DEFAULT_ISSUER, null))) {
            assertRejected("Bearer " + jwt, HttpStatus.UNAUTHORIZED);
        }
        assertThrows(IllegalArgumentException.class, () ->
            new JwtAuthenticationFilter(SECRET, "", JwtAuthenticationFilter.DEFAULT_AUDIENCE));
        assertThrows(IllegalArgumentException.class, () ->
            new JwtAuthenticationFilter(SECRET, JwtAuthenticationFilter.DEFAULT_ISSUER, ""));
    }

    @Test
    void spoofedIdentityHeadersAreStrippedOnPublicAuthRoutes() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Aetheris-User-Id", "attacker-rotating-1")
                        .header("X-Aetheris-User-Email", "attacker@example.com")
                        .header("X-Aetheris-User-Role", "ADMIN")
                        .header("X-Aetheris-Scopes", "users:write")
                        .header("Content-Type", "application/json")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        for (String name : JwtAuthenticationFilter.IDENTITY_HEADERS) {
            assertFalse(headers.containsKey(name), name + " must not reach identity-service or the rate limiter");
        }
        assertEquals("application/json", headers.getFirst("Content-Type"));
        assertNull(forwarded.get().getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
    }

    @Test
    void verifiedUserIdIsExposedOnlyAsAnExchangeAttribute() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        String token = token(List.of("users:read"), Instant.now().plusSeconds(300));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Aetheris-User-Id", "999")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertEquals("42", forwarded.get().getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
    }

    @Test
    void rejectedTokenNeverSetsVerifiedUserId() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer definitely-not-a-jwt")
                        .header("X-Aetheris-User-Id", "999")
                        .build());

        filter.filter(exchange, next -> Mono.empty()).block();

        assertNull(exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
    @Test
    void unknownRoutedPathRequiresBearerTokenByDefault() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/new-service/items").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void unknownRoutedPathIsForbiddenEvenWithValidTokenAndAllScopes() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
        String token = token(List.of("users:read", "users:write", "services:read", "orchestrator:read", "orchestrator:write"),
                Instant.now().plusSeconds(300));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/new-service/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void lookalikePublicPrefixesAreNotPublic() {
        assertFalse(JwtAuthenticationFilter.isPublicRoute("/api/authx/login"));
        assertFalse(JwtAuthenticationFilter.isPublicRoute("/api/auth"));
        assertFalse(JwtAuthenticationFilter.isPublicRoute("/API/auth/login"));
        assertTrue(JwtAuthenticationFilter.isPublicRoute("/api/auth/login"));
        assertNull(JwtAuthenticationFilter.requiredScope("/api/usersx", org.springframework.http.HttpMethod.GET));
        assertEquals("users:read", JwtAuthenticationFilter.requiredScope("/api/users", org.springframework.http.HttpMethod.GET));
    }

    @Test
    void ambiguousPathsAreRejectedBeforeAnyRoutingDecision() {
        for (String raw : List.of(
                "/api/auth/../users", "/api/auth/..%2Fusers", "/api/auth/%2e%2e/users", "/api/users;jsessionid=1",
                "/api//users", "/api/auth/%2Fusers", "/api/auth/%5Cusers", "/api/auth/%252e%252e/users", "/api/users/.")) {
            JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.method(org.springframework.http.HttpMethod.GET, java.net.URI.create("http://gateway" + raw)).build());
            AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

            filter.filter(exchange, next -> {
                forwarded.set(next);
                return Mono.empty();
            }).block();

            assertNull(forwarded.get(), raw);
            assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode(), raw);
        }
    }

    @Test
    void ordinaryPathsAreNotTreatedAsAmbiguous() {
        assertFalse(JwtAuthenticationFilter.isAmbiguousPath("/api/users/42"));
        assertFalse(JwtAuthenticationFilter.isAmbiguousPath("/api/orchestrator/knowledge/search"));
        assertFalse(JwtAuthenticationFilter.isAmbiguousPath("/api/auth/browser/login"));
    }
}
