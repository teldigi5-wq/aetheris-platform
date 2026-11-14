package io.aetheris.users.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B62-G5 ONLY: calls the actual servlet security filter, but revocation reader
 * is injected through a package-private constructor unavailable to Spring.
 * Never supplies real user tokens, customer DB records or production keys.
 */
class B62G5DirectRevocationServletTest {
    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KID = "b62g5-current";
    private static KeyPair signer;
    private static KeyPair intruder;

    @BeforeAll
    static void ephemeralRsa3072() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        signer = generator.generateKeyPair();
        intruder = generator.generateKeyPair();
    }

    private MockMvc mvc(DirectApiJwtFilter.TestOnlyRevocationDecision reader) {
        String publicKey = Base64.getEncoder().encodeToString(signer.getPublic().getEncoded());
        DirectApiJwtFilter filter = new DirectApiJwtFilter("",
            DirectApiJwtFilter.DEFAULT_ISSUER, DirectApiJwtFilter.DEFAULT_AUDIENCE,
            "rs256", publicKey, KID, "", "", "", CLOCK, reader);
        return MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(org.mockito.Mockito.mock(io.aetheris.users.UserService.class)))
            .addFilters(filter).build();
    }

    private String signed(KeyPair pair, String issuer, String audience,
                          String kid, Object uid, String role, String scope,
                          Instant iat, Instant expires) {
        return Jwts.builder()
            .issuer(issuer).audience().add(audience).and()
            .subject("synthetic-b62g5@example.invalid")
            .claim("uid", uid).claim("role", role)
            .claim("scopes", List.of(scope))
            .issuedAt(Date.from(iat)).expiration(Date.from(expires))
            .header().keyId(kid).and()
            .signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String signedGood() {
        return signed(signer, DirectApiJwtFilter.DEFAULT_ISSUER,
            DirectApiJwtFilter.DEFAULT_AUDIENCE, KID, 42L, "ADMIN",
            "users:read", NOW.minusSeconds(5), NOW.plusSeconds(300));
    }

    @Test
    void signedAuthorizedJwtConsultsReadOnlyCallbackWithOnlyVerifiedClaims() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MockMvc mvc = mvc((uid, issuer, kid, iat) -> {
            calls.incrementAndGet();
            assertEquals(42L, uid);
            assertEquals(DirectApiJwtFilter.DEFAULT_ISSUER, issuer);
            assertEquals(KID, kid);
            assertEquals(NOW.minusSeconds(5).getEpochSecond(), iat);
            return true;
        });
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + signedGood()))
            .andExpect(status().isOk());
        assertEquals(1, calls.get(), "One revocation check on a fully authorized request");
    }

    @Test
    void revocationDenialAndLookupExceptionsNeverReachBusinessController() throws Exception {
        MockMvc denied = mvc((uid, iss, kid, iat) -> false);
        denied.perform(get("/api/users").header("Authorization", "Bearer " + signedGood()))
            .andExpect(status().isUnauthorized());
        MockMvc outage = mvc((uid, iss, kid, iat) -> {
            throw new java.sql.SQLTimeoutException("test-only lookup timed out");
        });
        outage.perform(get("/api/users").header("Authorization", "Bearer " + signedGood()))
            .andExpect(status().isUnauthorized());
        MockMvc offline = mvc((uid, iss, kid, iat) -> {
            throw new java.io.IOException("test-only revocation DB offline");
        });
        offline.perform(get("/api/users").header("Authorization", "Bearer " + signedGood()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureIssuerAudienceUidOrAuthorizationNeverInvokesLookup() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MockMvc mvc = mvc((uid, iss, kid, iat) -> {
            calls.incrementAndGet();
            return true;
        });
        String[] rejected = {
            signed(intruder, DirectApiJwtFilter.DEFAULT_ISSUER,
                DirectApiJwtFilter.DEFAULT_AUDIENCE, KID, 42L, "ADMIN",
                "users:read", NOW.minusSeconds(5), NOW.plusSeconds(300)),
            signed(signer, "foreign-issuer", DirectApiJwtFilter.DEFAULT_AUDIENCE,
                KID, 42L, "ADMIN", "users:read", NOW.minusSeconds(5), NOW.plusSeconds(300)),
            signed(signer, DirectApiJwtFilter.DEFAULT_ISSUER, "foreign-audience",
                KID, 42L, "ADMIN", "users:read", NOW.minusSeconds(5), NOW.plusSeconds(300)),
            signed(signer, DirectApiJwtFilter.DEFAULT_ISSUER,
                DirectApiJwtFilter.DEFAULT_AUDIENCE, "other-kid", 42L,
                "ADMIN", "users:read", NOW.minusSeconds(5), NOW.plusSeconds(300)),
            signed(signer, DirectApiJwtFilter.DEFAULT_ISSUER,
                DirectApiJwtFilter.DEFAULT_AUDIENCE, KID, 42.5d,
                "ADMIN", "users:read", NOW.minusSeconds(5), NOW.plusSeconds(300)),
            signed(signer, DirectApiJwtFilter.DEFAULT_ISSUER,
                DirectApiJwtFilter.DEFAULT_AUDIENCE, KID, 42L,
                "ADMIN", "users:read", NOW.minusSeconds(7201), NOW.minusSeconds(1))
        };
        for (String token : rejected) {
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        }
        // Role/scope policy denies earlier, never treating emergency lookup
        // success as authority to bypass regular authorization checks.
        mvc.perform(get("/api/users").header("Authorization", "Bearer " +
            signed(signer, DirectApiJwtFilter.DEFAULT_ISSUER,
                DirectApiJwtFilter.DEFAULT_AUDIENCE, KID, 42L, "API_CONSUMER",
                "users:read", NOW.minusSeconds(5), NOW.plusSeconds(300))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " +
            signed(signer, DirectApiJwtFilter.DEFAULT_ISSUER,
                DirectApiJwtFilter.DEFAULT_AUDIENCE, KID, 42L, "ADMIN",
                "orchestrator:read", NOW.minusSeconds(5), NOW.plusSeconds(300))))
            .andExpect(status().isForbidden());
        assertEquals(0, calls.get(), "Invalid or unauthorized calls must not query revocation");
    }

    @Test
    void injectedRevocationCannotBeUsedInLegacyHmacMode() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectApiJwtFilter(
                "test-only-direct-backend-auth-hmac-secret-at-least-32-bytes",
                DirectApiJwtFilter.DEFAULT_ISSUER, DirectApiJwtFilter.DEFAULT_AUDIENCE,
                "hmac", "", "", "", "", "", CLOCK,
                (uid, issuer, kid, iat) -> true));
    }
}
