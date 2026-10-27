package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * B62-G7: shared loopback-only synthetic policy authority, real reactive
 * Gateway filter, boundedElastic test adapter. NO production HTTP client,
 * workload identity, database migration or live Gateway activation.
 */
class B62G7SharedHttpContractIT {
    private static final Instant NOW = Instant.parse("2026-10-27T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KID = "b62g7-synthetic-current";
    private static final String STAGE = System.getProperty("b62g7.stage", "");
    private static KeyPair rsa, intruder;

    @BeforeAll
    static void syntheticKeysAndStrictStage() throws Exception {
        assertTrue(List.of("baseline", "epoch", "blocked", "outage").contains(STAGE));
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(3072);
        rsa = g.generateKeyPair();
        intruder = g.generateKeyPair();
    }

    private static boolean fetchSyntheticDecision(long uid, String issuer, String kid, long iat) {
        if (uid < 1 || !JwtAuthenticationFilter.DEFAULT_ISSUER.equals(issuer)
                || !KID.equals(kid) || iat < 1) return false;
        String url = "http://127.0.0.1:15377/decision?uid=" + uid
            + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
            + "&kid=" + URLEncoder.encode(kid, StandardCharsets.UTF_8)
            + "&iat=" + iat;
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(100)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(140)).GET().build();
            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && "ALLOW".equals(response.body());
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception unavailableFixture) {
            return false;
        }
    }

    private JwtAuthenticationFilter filter(AtomicInteger calls) {
        return new JwtAuthenticationFilter("",
            JwtAuthenticationFilter.DEFAULT_ISSUER,
            JwtAuthenticationFilter.DEFAULT_AUDIENCE,
            "rs256", Base64.getEncoder().encodeToString(rsa.getPublic().getEncoded()),
            KID, "", "", "", CLOCK, (uid, issuer, kid, iat) ->
                Mono.fromCallable(() -> {
                    calls.incrementAndGet();
                    return fetchSyntheticDecision(uid, issuer, kid, iat);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private String bearer(KeyPair signer, long uid) {
        return Jwts.builder()
            .issuer(JwtAuthenticationFilter.DEFAULT_ISSUER)
            .audience().add(JwtAuthenticationFilter.DEFAULT_AUDIENCE).and()
            .subject("b62g7-synthetic@example.invalid")
            .claim("uid", uid).claim("role", "ADMIN")
            .claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(NOW.minusSeconds(5)))
            .expiration(Date.from(NOW.plusSeconds(300)))
            .header().keyId(KID).and()
            .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private MockServerWebExchange exchange(String bearer) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
            .header("X-Aetheris-User-Id", "999").build());
    }

    @Test
    void sharedFixtureDeniesRevokedIdentityAcrossActualGatewayWithNoForwarding() {
        AtomicInteger calls = new AtomicInteger();
        JwtAuthenticationFilter filter = filter(calls);
        boolean allow42 = "baseline".equals(STAGE);
        boolean allow43 = "baseline".equals(STAGE) || "epoch".equals(STAGE);
        for (long uid : new long[]{42L, 43L}) {
            MockServerWebExchange e = exchange(bearer(rsa, uid));
            AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
            filter.filter(e, accepted -> {
                forwarded.set(accepted);
                return Mono.empty();
            }).block(Duration.ofSeconds(3));
            boolean expected = uid == 42L ? allow42 : allow43;
            if (expected) {
                assertNotNull(forwarded.get(), "Legitimate synthetic JWT wrongly blocked");
                assertEquals(Long.toString(uid), forwarded.get().getRequest()
                    .getHeaders().getFirst("X-Aetheris-User-Id"));
            } else {
                assertNull(forwarded.get(), "Revoked JWT was forwarded");
                assertEquals(HttpStatus.UNAUTHORIZED, e.getResponse().getStatusCode());
                assertNull(e.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE));
            }
        }
        assertEquals(2, calls.get());
        MockServerWebExchange invalid = exchange(bearer(intruder, 42L));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(invalid, accepted -> {
            forwarded.set(accepted);
            return Mono.empty();
        }).block(Duration.ofSeconds(3));
        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getResponse().getStatusCode());
        assertEquals(2, calls.get(), "Invalid signature cannot contact policy authority");
        System.out.println("B62G7_GATEWAY_REAL_REACTIVE_FILTER_" +
            STAGE.toUpperCase() + "_DECISIONS_PASS");
    }
}
