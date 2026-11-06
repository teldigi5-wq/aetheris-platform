package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceRs256ModeTest {
    private static final java.security.KeyPair PAIR = newPair();
    private static final String PRIVATE = pem("PRIVATE KEY", PAIR.getPrivate().getEncoded());
    private static final String ISSUER = JwtService.DEFAULT_ISSUER;
    private static final String AUDIENCE = JwtService.DEFAULT_AUDIENCE;
    private static final String KID = "aetheris-rs256-review-a";
    private static java.security.KeyPair newPair() {
        try {
            var gen = java.security.KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (java.security.GeneralSecurityException failure) {
            throw new AssertionError("Synthetic RSA key generation unavailable");
        }
    }

    private static String pem(String kind, byte[] bytes) {
        return "-----BEGIN " + kind + "-----\\n" +
                java.util.Base64.getEncoder().encodeToString(bytes) +
                "\\n-----END " + kind + "-----";
    }

    private static IdentityAccount account() {
        var account = new IdentityAccount("Synthetic", "only.test@example.test", "no-hash", Role.ADMIN);
        ReflectionTestUtils.setField(account, "id", 9L);
        return account;
    }

    @Test
    void identitySignsOnlyWithReviewedPrivateKeyAndPinnedKid() {
        var issuer = new JwtService("", 900, ISSUER, AUDIENCE, "RS256", PRIVATE, KID);
        String token = issuer.issue(account());
        var parsed = Jwts.parser()
                .verifyWith(PAIR.getPublic())
                .requireIssuer(ISSUER).requireAudience(AUDIENCE)
                .build().parseSignedClaims(token);
        assertEquals("RS256", parsed.getHeader().getAlgorithm());
        assertEquals(KID, parsed.getHeader().getKeyId());
        assertEquals(9L, ((Number) parsed.getPayload().get("uid")).longValue());
        assertEquals("ADMIN", parsed.getPayload().get("role", String.class));
        assertNotNull(parsed.getPayload().getIssuedAt());
        assertNotNull(parsed.getPayload().getExpiration());
    }

    @Test
    void issuerRefusesMixedKeysWeakKeysAndUnsupportedAlgorithms() {
        String shared = "synthetic-long-legacy-hs-signing-secret-0123456789";
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(shared, 900, ISSUER, AUDIENCE, "RS256", PRIVATE, KID));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("", 900, ISSUER, AUDIENCE, "HS256", PRIVATE, KID));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("", 900, ISSUER, AUDIENCE, "RS256", PRIVATE, "../kid"));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("", 900, ISSUER, AUDIENCE, "RS256", "", KID));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("", 900, ISSUER, AUDIENCE, "HS512", PRIVATE, KID));
    }

    @Test
    void privateSigningKeyMustBeAtLeast2048BitRSA() throws Exception {
        var weakGen = java.security.KeyPairGenerator.getInstance("RSA");
        weakGen.initialize(1024);
        var weak = weakGen.generateKeyPair();
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("", 900, ISSUER, AUDIENCE, "RS256",
                        pem("PRIVATE KEY", weak.getPrivate().getEncoded()), KID));
    }

    @Test
    void rs256OnlyOutputCannotBeReadAsHs256() {
        String jwt = new JwtService("", 900, ISSUER, AUDIENCE,
                "RS256", PRIVATE, KID).issue(account());
        assertThrows(io.jsonwebtoken.JwtException.class,
                () -> Jwts.parser().verifyWith(Keys.hmacShaKeyFor(
                        "different-long-synthetic-hs-only-secret-0123456789".getBytes(StandardCharsets.UTF_8)))
                        .build().parseSignedClaims(jwt));
    }
}
