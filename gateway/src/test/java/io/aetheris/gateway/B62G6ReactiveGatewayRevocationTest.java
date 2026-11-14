package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B62-G6 test-only publisher seam in the real Spring Cloud Gateway filter.
 * No JDBC, live key, production bean or deployed emergency revocation setting.
 */
class B62G6ReactiveGatewayRevocationTest {
    private static final Instant NOW = Instant.parse("2026-10-27T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KID = "b62g6-current";
    private static KeyPair signer;
    private static KeyPair unknownSigner;

    @BeforeAll
    static void ephemeral3072BitRsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        signer = generator.generateKeyPair();
        unknownSigner = generator.generateKeyPair();
    }

    private JwtAuthenticationFilter filter(JwtAuthenticationFilter.TestOnlyReactiveRevocation reader) {
        return new JwtAuthenticationFilter("",
            JwtAuthenticationFilter.DEFAULT_ISSUER, JwtAuthenticationFilter.DEFAULT_AUDIENCE,
            "rs256", Base64.getEncoder().encodeToString(signer.getPublic().getEncoded()),
            KID, "", "", "", CLOCK, reader);
    }

    private String jwt(KeyPair key, String kid, String issuer, String audience,
                       Object uid, String role, String scope,
                       Instant issued, Instant expiry) {
        return Jwts.builder()
            .issuer(issuer).audience().add(audience).and()
            .subject("test-only-b62g6@example.invalid")
            .claim("uid", uid).claim("role", role)
            .claim("scopes", List.of(scope))
            .issuedAt(Date.from(issued)).expiration(Date.from(expiry))
            .header().keyId(kid).and()
            .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String valid() {
        return jwt(signer, KID, JwtAuthenticationFilter.DEFAULT_ISSUER,
            JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42L, "ADMIN", "users:read",
            NOW.minusSeconds(5), NOW.plusSeconds(300));
    }

    private MockServerWebExchange exchange(String jwt) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
            .header("X-Aetheris-User-Id", "999")
            .header("X-Aetheris-User-Email", "forged@example.invalid")
            .header("X-Aetheris-User-Role", "ADMIN")
            .header("X-Aetheris-Scopes", "users:write").build());
    }

    private AtomicReference<ServerWebExchange> run(JwtAuthenticationFilter f,
                                                    MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        f.filter(exchange, authenticated -> {
            forwarded.set(authenticated);
            return Mono.empty();
        }).block(Duration.ofSeconds(2));
        return forwarded;
    }

    @Test
    void admitsOnlyAfterSubscriptionToGenuineAsyncReaderAndThenStripsSpoofedHeaders() {
        AtomicInteger calls = new AtomicInteger();
        JwtAuthenticationFilter f = filter((uid, iss, kid, iat) -> {
            calls.incrementAndGet();
            assertEquals(42L, uid);
            assertEquals(JwtAuthenticationFilter.DEFAULT_ISSUER, iss);
            assertEquals(KID, kid);
            assertEquals(NOW.minusSeconds(5).getEpochSecond(), iat);
            return Mono.delay(Duration.ofMillis(15)).map(tick -> true);
        });
        MockServerWebExchange e = exchange(valid());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = accepted -> {
            forwarded.set(accepted);
            return Mono.empty();
        };
        Mono<Void> action = f.filter(e, chain);
        assertEquals(0, calls.get(), "Reactive reader must be deferred until subscription");
        assertNull(e.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        action.block(Duration.ofSeconds(2));
        assertEquals(1, calls.get());
        assertNotNull(forwarded.get());
        assertEquals("42", forwarded.get().getRequest().getHeaders()
            .getFirst("X-Aetheris-User-Id"));
        assertEquals("test-only-b62g6@example.invalid", forwarded.get()
            .getRequest().getHeaders().getFirst("X-Aetheris-User-Email"));
        assertEquals("users:read", forwarded.get().getRequest().getHeaders()
            .getFirst("X-Aetheris-Scopes"));
        assertEquals("42", e.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
    }

    @Test
    void denialEmptyExceptionNullAndNeverCompletingPublisherFailClosedWithoutIdentityLeak() {
        List<JwtAuthenticationFilter.TestOnlyReactiveRevocation> readers = List.of(
            (uid, iss, kid, iat) -> Mono.just(false),
            (uid, iss, kid, iat) -> Mono.empty(),
            (uid, iss, kid, iat) -> Mono.error(new java.io.IOException("synthetic DB offline")),
            (uid, iss, kid, iat) -> { throw new IllegalStateException("synthetic client failure"); },
            (uid, iss, kid, iat) -> null,
            (uid, iss, kid, iat) -> Mono.never()
        );
        for (int i=0; i<readers.size(); i++) {
            MockServerWebExchange e = exchange(valid());
            AtomicReference<ServerWebExchange> forwarded = run(filter(readers.get(i)), e);
            assertNull(forwarded.get(), "Revocation deny/timeout routed a request: " + i);
            assertNull(e.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
            assertEquals(HttpStatus.UNAUTHORIZED, e.getResponse().getStatusCode());
        }
    }

    @Test
    void signedInvalidOrUnauthorizedJwtCannotCallReactiveStoreOrPoisonForwardedIdentity() {
        AtomicInteger calls = new AtomicInteger();
        JwtAuthenticationFilter f = filter((uid, iss, kid, iat) -> {
            calls.incrementAndGet();
            return Mono.just(true);
        });
        record Reject(String jwt, HttpStatus status) {}
        List<Reject> rejected = List.of(
            new Reject(jwt(unknownSigner, KID, JwtAuthenticationFilter.DEFAULT_ISSUER,
                JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42L,"ADMIN","users:read",
                NOW.minusSeconds(5),NOW.plusSeconds(300)), HttpStatus.UNAUTHORIZED),
            new Reject(jwt(signer, "other-kid", JwtAuthenticationFilter.DEFAULT_ISSUER,
                JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42L,"ADMIN","users:read",
                NOW.minusSeconds(5),NOW.plusSeconds(300)), HttpStatus.UNAUTHORIZED),
            new Reject(jwt(signer, KID, "other-issuer",
                JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42L,"ADMIN","users:read",
                NOW.minusSeconds(5),NOW.plusSeconds(300)), HttpStatus.UNAUTHORIZED),
            new Reject(jwt(signer, KID, JwtAuthenticationFilter.DEFAULT_ISSUER,
                "other-audience", 42L,"ADMIN","users:read",
                NOW.minusSeconds(5),NOW.plusSeconds(300)), HttpStatus.UNAUTHORIZED),
            new Reject(jwt(signer, KID, JwtAuthenticationFilter.DEFAULT_ISSUER,
                JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42.5d,"ADMIN","users:read",
                NOW.minusSeconds(5),NOW.plusSeconds(300)), HttpStatus.UNAUTHORIZED),
            new Reject(jwt(signer, KID, JwtAuthenticationFilter.DEFAULT_ISSUER,
                JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42L,"ADMIN","users:read",
                NOW.minusSeconds(7200),NOW.minusSeconds(1)), HttpStatus.UNAUTHORIZED),
            new Reject(jwt(signer, KID, JwtAuthenticationFilter.DEFAULT_ISSUER,
                JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42L,"API_CONSUMER","users:read",
                NOW.minusSeconds(5),NOW.plusSeconds(300)), HttpStatus.FORBIDDEN),
            new Reject(jwt(signer, KID, JwtAuthenticationFilter.DEFAULT_ISSUER,
                JwtAuthenticationFilter.DEFAULT_AUDIENCE, 42L,"ADMIN","orchestrator:read",
                NOW.minusSeconds(5),NOW.plusSeconds(300)), HttpStatus.FORBIDDEN)
        );
        for (Reject item : rejected) {
            MockServerWebExchange e = exchange(item.jwt());
            assertNull(run(f,e).get());
            assertEquals(item.status(),e.getResponse().getStatusCode());
            assertNull(e.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
        }
        assertEquals(0,calls.get(), "Invalid claims must never reach revocation lookup");
    }

    @Test
    void injectedProofCannotBeConfiguredForLegacyHmacVerifier() {
        assertThrows(IllegalArgumentException.class, () ->
            new JwtAuthenticationFilter("test-only-direct-backend-auth-hmac-secret-at-least-32-bytes",
                JwtAuthenticationFilter.DEFAULT_ISSUER, JwtAuthenticationFilter.DEFAULT_AUDIENCE,
                "hmac", "", "", "", "", "", CLOCK,
                (uid, issuer, kid, iat) -> Mono.just(true)));
    }
}
