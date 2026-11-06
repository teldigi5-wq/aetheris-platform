package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtTenantScopeBoundaryTest {
    private static final String SECRET = "synthetic-gateway-tenant-boundary-secret-0123456789";
    private static final String TENANT = "e398c9b9-23ea-473a-8240-8c42a92f7a34";

    private String jwt(String role, List<String> scopes, String tenant) {
        var builder = Jwts.builder()
                .issuer(JwtAuthenticationFilter.DEFAULT_ISSUER)
                .audience().add(JwtAuthenticationFilter.DEFAULT_AUDIENCE).and()
                .subject("synthetic-account@example.test")
                .claim("uid", 17L).claim("role", role)
                .claim("scopes", scopes)
                .issuedAt(Date.from(Instant.now().minusSeconds(20)))
                .expiration(Date.from(Instant.now().plusSeconds(150)));
        if (tenant != null) builder.claim("tid", tenant);
        return builder.signWith(Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    private static class Outcome {
        final HttpStatus status;
        final ServerWebExchange forwarded;
        Outcome(HttpStatus status, ServerWebExchange forwarded) {
            this.status = status;
            this.forwarded = forwarded;
        }
    }

    private Outcome call(String method, String path, String jwt, String claimedHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = switch (method) {
            case "POST" -> MockServerHttpRequest.post(path);
            case "DELETE" -> MockServerHttpRequest.delete(path);
            default -> MockServerHttpRequest.get(path);
        };
        if (jwt != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        if (claimedHeader != null) request.header("X-Aetheris-Tenant-Id", claimedHeader);
        var exchange = MockServerWebExchange.from(request.build());
        var received = new AtomicReference<ServerWebExchange>();
        new JwtAuthenticationFilter(SECRET).filter(exchange, accepted -> {
            received.set(accepted);
            return Mono.empty();
        }).block();
        return new Outcome((HttpStatus) exchange.getResponse().getStatusCode(), received.get());
    }

    @Test
    void tenantReadIsAllowedOnlyWithSignedTenantIdAndReadScope() {
        var outcome = call("GET", "/api/users/tenant",
                jwt("API_CONSUMER", List.of("tenant:users:read"), TENANT), "forged-tenant");
        assertNotNull(outcome.forwarded);
        assertNull(outcome.forwarded.getRequest().getHeaders().getFirst("X-Aetheris-Tenant-Id"));
        assertEquals("17", outcome.forwarded.getAttribute(
                JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
    }

    @Test
    void tenantWriteNeedsDedicatedWriteScope() {
        assertEquals(HttpStatus.FORBIDDEN,
                call("POST", "/api/users/tenant",
                        jwt("API_CONSUMER", List.of("tenant:users:read"), TENANT),
                        null).status);
        assertNotNull(call("POST", "/api/users/tenant",
                jwt("API_CONSUMER", List.of("tenant:users:write"), TENANT),
                null).forwarded);
    }

    @Test
    void malformedAbsentOrNonCanonicalSignedTenantDenied() {
        for (String id : new String[]{null, "not-a-uuid",
                "E398C9B9-23EA-473A-8240-8C42A92F7A34", "00000000-0000-0000-0000-0"}) {
            assertEquals(HttpStatus.UNAUTHORIZED, call("GET", "/api/users/tenant",
                    jwt("API_CONSUMER", List.of("tenant:users:read"), id), null).status);
        }
    }

    @Test
    void globalUserEndpointNeverBecomesConsumerAccessible() {
        assertEquals(HttpStatus.FORBIDDEN, call("GET", "/api/users",
                jwt("API_CONSUMER", List.of("users:read", "tenant:users:read"), TENANT),
                null).status);
        assertEquals(HttpStatus.FORBIDDEN, call("GET", "/api/users/tenants/123",
                jwt("API_CONSUMER", List.of("tenant:users:read"), TENANT), null).status);
    }

    @Test
    void signedGlobalAdminCannotUseTenantAccountPath() {
        assertEquals(HttpStatus.FORBIDDEN, call("GET", "/api/users/tenant",
                jwt("ADMIN", List.of("users:read", "tenant:users:read"), TENANT),
                null).status);
    }

    @Test
    void forgingTenantHeaderWithoutBearerNeverPasses() {
        assertEquals(HttpStatus.UNAUTHORIZED, call("GET", "/api/users/tenant",
                null, TENANT).status);
    }
}
