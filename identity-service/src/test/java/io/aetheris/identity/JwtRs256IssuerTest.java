package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Real token issuer signs synthetic claims with fresh ephemeral RSA keys. */
class JwtRs256IssuerTest {
    private static final String SECRET = "SYNTHETIC_COMPATIBILITY_HMAC_NOT_FOR_PRODUCTION_20261027";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-user-api";

    private static KeyPair generate(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static String pem(String kind, byte[] content) {
        return "-----BEGIN " + kind + "-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(content)
            + "\n-----END " + kind + "-----";
    }

    private static IdentityAccount account() {
        var user = new IdentityAccount("Synthetic", "user@example.invalid", "not-password", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    @Test
    void realIdentitySignsSingleAudienceJwtWithRs256AndNoTenant() throws Exception {
        KeyPair pair = generate(2048);
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        JwtService issuer = new JwtService(SECRET, 3600, ISSUER, AUDIENCE, true, privatePem);
        String signed = issuer.issue(account());
        var verified = Jwts.parser().verifyWith(pair.getPublic()).build().parseSignedClaims(signed);
        assertEquals("RS256", verified.getHeader().getAlgorithm());
        assertEquals(ISSUER, verified.getPayload().getIssuer());
        assertEquals(java.util.Set.of(AUDIENCE), verified.getPayload().getAudience());
        assertNull(verified.getPayload().get("tenant_id"));
        assertThrows(Exception.class, () -> Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(signed));
        String delegated = issuer.issueForApprovedAudience(account(), ApprovedTokenAudience.AUDIT);
        var scoped = Jwts.parser().verifyWith(pair.getPublic())
            .build().parseSignedClaims(delegated);
        assertEquals(java.util.Set.of("aetheris-audit-api"), scoped.getPayload().getAudience());
        assertEquals(java.util.Set.of("services:read"),
            java.util.Set.copyOf(scoped.getPayload().get("scopes", java.util.List.class)));
    }

    @Test
    void malformedAndWeakPrivateKeysNeverDowngradeToHmac() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 3600, ISSUER, AUDIENCE, true, "junk"));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 3600, "", "", false, " "));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 3600, "", "", false,
                pem("PRIVATE KEY", generate(2048).getPrivate().getEncoded())));
        String weak = pem("PRIVATE KEY", generate(1024).getPrivate().getEncoded());
        assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 3600, ISSUER, AUDIENCE, false, weak));
    }

    @Test
    void legacySignatureStillValidOnlyWithExplicitlyUnconfiguredRs256() throws Exception {
        String hmac = new JwtService(SECRET, 3600).issue(account());
        var verified = Jwts.parser().verifyWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(hmac);
        // JJWT chooses an HMAC digest strength based on secret length.
        // The legacy contract promised HMAC, not one specific HS variant.
        assertTrue(verified.getHeader().getAlgorithm().startsWith("HS"));
    }
}
