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

/** Real Gateway GlobalFilter with signed, synthetic adversarial identity claims. */
class GatewayPrincipalBoundaryTest {
    private static final String SECRET =
        "SYNTHETIC_GATEWAY_PRINCIPAL_VALIDATION_TEST_KEY_20261027_LENGTH";
    private static final JwtAuthenticationFilter FILTER = new JwtAuthenticationFilter(SECRET);

    private String jwt(Object uid, String role, Object scopes,
                       String subject, Instant issued, Instant expires) {
        var builder = Jwts.builder()
            .subject(subject).claim("uid", uid).claim("role", role)
            .claim("scopes", scopes);
        if (issued != null) builder.issuedAt(Date.from(issued));
        if (expires != null) builder.expiration(Date.from(expires));
        return builder.signWith(Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String token(Object uid, String role, Object scopes) {
        Instant now = Instant.now();
        return jwt(uid, role, scopes, "consumer@example.invalid",
            now.minusSeconds(5), now.plusSeconds(240));
    }

    private void request(String route, String jwt, HttpStatus expected,
                         boolean shouldForward) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Aetheris-User-Id", "999999")
                .header("X-Aetheris-User-Role", "ADMIN")
                .header("X-Aetheris-Tenant-Id", "untrusted-tenant").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = incoming -> {
            forwarded.set(incoming);
            return Mono.empty();
        };
        FILTER.filter(exchange, chain).block();
        if (shouldForward) {
            assertNotNull(forwarded.get());
            assertEquals("42", forwarded.get().getRequest().getHeaders()
                .getFirst("X-Aetheris-User-Id"));
            assertNull(forwarded.get().getRequest().getHeaders()
                .getFirst("X-Aetheris-Tenant-Id"));
        } else {
            assertNull(forwarded.get());
            assertEquals(expected, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void validConsumerOrchestratorReadAndPrivilegedUserAuditRequestsWork() {
        request("/api/orchestrator", token(42L, "API_CONSUMER",
            List.of("orchestrator:read")), null, true);
        request("/api/users", token(42L, "ADMIN",
            List.of("users:read")), null, true);
        request("/api/events", token(42L, "DEVELOPER",
            List.of("services:read")), null, true);
    }

    @Test
    void consumerCannotReadDirectUsersOrAuditRegardlessOfLegacyScopes() {
        request("/api/users", token(42L, "API_CONSUMER",
            List.of("users:read")), HttpStatus.FORBIDDEN, false);
        request("/api/users/42", token(42L, "API_CONSUMER",
            List.of("users:read")), HttpStatus.FORBIDDEN, false);
        request("/api/events", token(42L, "API_CONSUMER",
            List.of("services:read")), HttpStatus.FORBIDDEN, false);
    }

    @Test
    void forgedRoleOrClaimedScopeOutsideActualRoleCeilingIsUnauthorized() {
        request("/api/orchestrator", token(42L, "ROOT",
            List.of("orchestrator:read")), HttpStatus.UNAUTHORIZED, false);
        request("/api/orchestrator", token(42L, "API_CONSUMER",
            List.of("orchestrator:write")), HttpStatus.UNAUTHORIZED, false);
        request("/api/orchestrator", token(42L, "DEVELOPER",
            List.of("identity:write", "orchestrator:read")),
            HttpStatus.UNAUTHORIZED, false);
    }

    @Test
    void untrustedUidFormatAndMissingOrBadTimestampsNeverBecomePrincipal() {
        Instant now = Instant.now();
        Object[] badUid = {0L, -3L, "42", 42.5};
        for (Object uid : badUid) {
            request("/api/orchestrator", token(uid, "API_CONSUMER",
                List.of("orchestrator:read")), HttpStatus.UNAUTHORIZED, false);
        }
        request("/api/orchestrator", jwt(42L, "API_CONSUMER",
            List.of("orchestrator:read"), "consumer@example.invalid",
            null, now.plusSeconds(240)), HttpStatus.UNAUTHORIZED, false);
        request("/api/orchestrator", jwt(42L, "API_CONSUMER",
            List.of("orchestrator:read"), "consumer@example.invalid",
            now.plusSeconds(120), now.plusSeconds(300)),
            HttpStatus.UNAUTHORIZED, false);
        request("/api/orchestrator", jwt(42L, "API_CONSUMER",
            List.of("orchestrator:read"), "consumer@example.invalid",
            now.minusSeconds(500), now.minusSeconds(10)),
            HttpStatus.UNAUTHORIZED, false);
    }

    @Test
    void untypedScopesNullScopesMixedCollectionAndOversizedScopeAreDenied() {
        request("/api/orchestrator", token(42L, "API_CONSUMER",
            "orchestrator:read"), HttpStatus.UNAUTHORIZED, false);
        request("/api/orchestrator", token(42L, "API_CONSUMER",
            null), HttpStatus.UNAUTHORIZED, false);
        request("/api/orchestrator", token(42L, "API_CONSUMER",
            List.of("orchestrator:read", 9)), HttpStatus.UNAUTHORIZED, false);
        request("/api/orchestrator", token(42L, "API_CONSUMER",
            List.of("orchestrator:read", "x".repeat(81))),
            HttpStatus.UNAUTHORIZED, false);
    }
}
