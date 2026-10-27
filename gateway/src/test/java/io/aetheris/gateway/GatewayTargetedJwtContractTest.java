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

/** Tests real Gateway GlobalFilter with signed target JWTs, no network/deploy. */
class GatewayTargetedJwtContractTest {
    private static final String SECRET =
        "SYNTHETIC_GATEWAY_JWT_SIGNING_KEY_NOT_FOR_PRODUCTION_20261027_LONG";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String USERS = "aetheris-user-api";
    private static final String AUDIT = "aetheris-audit-api";
    private static final String ORCH = "aetheris-orchestrator-api";

    private JwtAuthenticationFilter filter(String issuer, String users,
                                            String audit, String orchestrator) {
        return new JwtAuthenticationFilter(SECRET, issuer, users, audit, orchestrator);
    }

    private String jwt(String issuer, List<String> audiences, String scope) {
        Instant now = Instant.now();
        var b = Jwts.builder().subject("synthetic@example.invalid")
            .claim("uid", 42L).claim("role", "ADMIN")
            .claim("scopes", List.of(scope))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)));
        if (issuer != null) b.issuer(issuer);
        if (audiences != null) b.claim("aud", audiences);
        return b.signWith(Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private ServerWebExchange forwarded(JwtAuthenticationFilter f, String path,
                                        String jwt, HttpStatus rejectedStatus) {
        MockServerWebExchange e = MockServerWebExchange.from(
            MockServerHttpRequest.get(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Aetheris-Tenant-Id", "attacker-tenant")
                .header("X-Aetheris-User-Id", "999").build());
        AtomicReference<ServerWebExchange> result = new AtomicReference<>();
        GatewayFilterChain chain = incoming -> { result.set(incoming); return Mono.empty(); };
        f.filter(e, chain).block();
        if (rejectedStatus != null) {
            assertNull(result.get());
            assertEquals(rejectedStatus, e.getResponse().getStatusCode());
        } else {
            assertNotNull(result.get());
            assertNull(result.get().getRequest().getHeaders().getFirst("X-Aetheris-Tenant-Id"));
            assertEquals("42", result.get().getRequest().getHeaders().getFirst("X-Aetheris-User-Id"));
        }
        return result.get();
    }

    @Test
    void exactSignedTargetAdmitsOnlyTheIntendedServiceRoute() {
        JwtAuthenticationFilter f = filter(ISSUER, USERS, AUDIT, ORCH);
        String userJwt = jwt(ISSUER, List.of(USERS), "users:read");
        forwarded(f, "/api/users", userJwt, null);
        forwarded(f, "/api/events", userJwt, HttpStatus.UNAUTHORIZED);
        forwarded(f, "/api/orchestrator", userJwt, HttpStatus.UNAUTHORIZED);
        forwarded(f, "/api/events", jwt(ISSUER, List.of(AUDIT), "services:read"), null);
        forwarded(f, "/api/orchestrator", jwt(ISSUER, List.of(ORCH), "orchestrator:read"), null);
    }

    @Test
    void missingIssuerMissingAudienceAndMultipleRecipientsAreRejected() {
        JwtAuthenticationFilter f = filter(ISSUER, USERS, AUDIT, ORCH);
        forwarded(f, "/api/users", jwt(null, List.of(USERS), "users:read"),
            HttpStatus.UNAUTHORIZED);
        forwarded(f, "/api/users", jwt(ISSUER, null, "users:read"),
            HttpStatus.UNAUTHORIZED);
        forwarded(f, "/api/users", jwt("https://untrusted.invalid", List.of(USERS), "users:read"),
            HttpStatus.UNAUTHORIZED);
        forwarded(f, "/api/users", jwt(ISSUER, List.of(USERS, AUDIT), "users:read"),
            HttpStatus.UNAUTHORIZED);
        forwarded(f, "/api/users", jwt(ISSUER, List.of(AUDIT), "users:read"),
            HttpStatus.UNAUTHORIZED);
        forwarded(f, "/api/users", jwt(ISSUER, List.of(USERS), "services:read"),
            HttpStatus.FORBIDDEN);
    }

    @Test
    void partiallyConfiguredRouteFailsClosedWith503NotLegacyFallback() {
        String valid = jwt(ISSUER, List.of(USERS), "users:read");
        forwarded(filter(ISSUER, "", "", ""), "/api/users", valid,
            HttpStatus.SERVICE_UNAVAILABLE);
        forwarded(filter("", USERS, "", ""), "/api/users", valid,
            HttpStatus.SERVICE_UNAVAILABLE);
        forwarded(filter(" ", USERS, "", ""), "/api/users", valid,
            HttpStatus.SERVICE_UNAVAILABLE);
        forwarded(filter(ISSUER, " ", "", ""), "/api/users", valid,
            HttpStatus.SERVICE_UNAVAILABLE);
        // An intentionally staged User audience does NOT allow unsafely
        // accepting the same bearer token on Audit or Orchestrator.
        forwarded(filter(ISSUER, USERS, "", ""), "/api/events",
            jwt(ISSUER, List.of(AUDIT), "services:read"),
            HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void emptyPolicyPreservesLegacyWhileStrippingSpoofedTenantHeader() {
        JwtAuthenticationFilter f = filter("", "", "", "");
        forwarded(f, "/api/users", jwt(null, null, "users:read"), null);
        // No tenancy was claimed. This is only legacy compatibility.
    }
}
