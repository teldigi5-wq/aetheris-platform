package io.aetheris.users.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I6 (B62/S0 readiness §5): direct User-service calls must reject algorithm
 * confusion and key-id mismatches in BOTH verifier modes, independently of the
 * gateway. Keys are generated in memory per run.
 */
class DirectApiAlgorithmConfusionTest {
    private static final String SECRET = "i6-test-only-direct-hmac-secret-at-least-32-bytes";
    private static final String KID = "i6-current";
    private static KeyPair current;
    private static KeyPair attacker;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        current = generator.generateKeyPair();
        attacker = generator.generateKeyPair();
    }

    private static MockMvc mvc(DirectApiJwtFilter filter) {
        return MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(
                org.mockito.Mockito.mock(io.aetheris.users.UserService.class)))
            .addFilters(filter).build();
    }

    private static JwtBuilder claims() {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(DirectApiJwtFilter.DEFAULT_ISSUER)
            .audience().add(DirectApiJwtFilter.DEFAULT_AUDIENCE).and()
            .subject("i6-admin@example.invalid")
            .claim("uid", 42L).claim("role", "ADMIN").claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)));
    }

    private static String rs256(KeyPair signer, String kid) {
        JwtBuilder builder = claims();
        if (kid != null) builder.header().keyId(kid).and();
        return builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String hs256(byte[] secret) {
        return claims().signWith(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"), Jwts.SIG.HS256).compact();
    }

    private static void expect(MockMvc mvc, String token, int code) throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
            .andExpect(status().is(code));
    }

    @Test
    void hmacModeRejectsUnsignedAndRsaTokens() throws Exception {
        MockMvc mvc = mvc(new DirectApiJwtFilter(SECRET));
        expect(mvc, hs256(SECRET.getBytes(StandardCharsets.UTF_8)), 200);
        expect(mvc, claims().compact(), 401);              // alg none
        expect(mvc, rs256(current, KID), 401);             // RS256 in hmac mode
    }

    @Test
    void rs256ModeRejectsConfusionAndKeyIdMismatch() throws Exception {
        String publicKey = Base64.getEncoder().encodeToString(current.getPublic().getEncoded());
        MockMvc mvc = mvc(new DirectApiJwtFilter("", DirectApiJwtFilter.DEFAULT_ISSUER,
            DirectApiJwtFilter.DEFAULT_AUDIENCE, "rs256", publicKey, KID));
        expect(mvc, rs256(current, KID), 200);
        expect(mvc, claims().compact(), 401);                              // alg none
        expect(mvc, hs256(current.getPublic().getEncoded()), 401);         // HS256 keyed by RSA public key
        expect(mvc, hs256(SECRET.getBytes(StandardCharsets.UTF_8)), 401);  // HS256 in rs256 mode
        expect(mvc, rs256(attacker, KID), 401);                            // untrusted key, trusted kid
        expect(mvc, rs256(current, null), 401);                            // missing kid
        expect(mvc, rs256(current, "unknown-kid"), 401);                   // unknown kid
    }
}
