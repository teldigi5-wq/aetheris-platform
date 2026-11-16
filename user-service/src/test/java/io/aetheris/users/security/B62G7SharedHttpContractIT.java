package io.aetheris.users.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B62-G7 synthetic shared HTTP fixture. Real servlet filter, but NOT a
 * production revocation transport, enabled Spring config or durable database.
 */
class B62G7SharedHttpContractIT {
    private static final Instant NOW = Instant.parse("2026-10-27T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KID = "b62g7-synthetic-current";
    private static final String STAGE = System.getProperty("b62g7.stage", "");
    private static KeyPair rsa, intruder;

    @BeforeAll
    static void syntheticKeysAndStrictStage() throws Exception {
        assertTrue(List.of("baseline", "epoch", "blocked", "outage").contains(STAGE),
            "B62G7 stage supplied by exact-head CI harness only");
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(3072);
        rsa = g.generateKeyPair();
        intruder = g.generateKeyPair();
    }

    private static boolean fetchSyntheticDecision(long uid, String issuer, String kid, long iat) {
        if (uid < 1 || !DirectApiJwtFilter.DEFAULT_ISSUER.equals(issuer)
                || !KID.equals(kid) || iat < 1) return false;
        // This pinned localhost-only CI URL is NOT remotely configurable.
        String base = "http://127.0.0.1:15377/decision?uid=" + uid;
        String query = base + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&kid=" + URLEncoder.encode(kid, StandardCharsets.UTF_8)
                + "&iat=" + iat;
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(150)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(query))
                .timeout(Duration.ofMillis(170)).GET().build();
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

    private MockMvc mvc(AtomicInteger calls) {
        DirectApiJwtFilter filter = new DirectApiJwtFilter("",
            DirectApiJwtFilter.DEFAULT_ISSUER, DirectApiJwtFilter.DEFAULT_AUDIENCE,
            "rs256", Base64.getEncoder().encodeToString(rsa.getPublic().getEncoded()),
            KID, "", "", "", CLOCK, (uid, issuer, kid, iat) -> {
                calls.incrementAndGet();
                return fetchSyntheticDecision(uid, issuer, kid, iat);
            });
        return MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(org.mockito.Mockito.mock(io.aetheris.users.UserService.class)))
            .addFilters(filter).build();
    }

    private String bearer(KeyPair signer, long uid) {
        return Jwts.builder()
            .issuer(DirectApiJwtFilter.DEFAULT_ISSUER)
            .audience().add(DirectApiJwtFilter.DEFAULT_AUDIENCE).and()
            .subject("b62g7-synthetic@example.invalid")
            .claim("uid", uid).claim("role", "ADMIN")
            .claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(NOW.minusSeconds(5)))
            .expiration(Date.from(NOW.plusSeconds(300)))
            .header().keyId(KID).and()
            .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    void sameSyntheticAuthorityEnforcesCrossServicePrincipalAndKeyStates() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MockMvc api = mvc(calls);
        boolean allow42 = "baseline".equals(STAGE);
        boolean allow43 = "baseline".equals(STAGE) || "epoch".equals(STAGE);
        for (long uid : new long[]{42L, 43L}) {
            int expected = (uid == 42L ? allow42 : allow43) ? 200 : 401;
            api.perform(get("/api/users")
                .header("Authorization", "Bearer " + bearer(rsa, uid)))
                .andExpect(status().is(expected));
        }
        assertEquals(2, calls.get(), "Each valid JWT must consult current fixture state");

        // A wrong-key signed JWT must be denied BEFORE any fixture request.
        api.perform(get("/api/users")
            .header("Authorization", "Bearer " + bearer(intruder, 42L)))
            .andExpect(status().isUnauthorized());
        assertEquals(2, calls.get(), "Invalid JWT cannot consult revocation authority");
        System.out.println("B62G7_USER_REAL_FILTER_"
            + STAGE.toUpperCase() + "_DECISIONS_PASS");
    }
}
