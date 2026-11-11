package io.aetheris.identity;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSecretPolicyTest {

    private static String randomHexSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Test
    void acceptsRandomlyGeneratedSecret() {
        String secret = randomHexSecret();
        assertEquals(secret, JwtSecretPolicy.requireStrong(secret));
    }

    @Test
    void missingOrBlankSecretFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong(null));
        assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong(""));
        assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong("   "));
    }

    @Test
    void shortSecretFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong(randomHexSecret().substring(0, 31)));
    }

    @Test
    void everyPublishedSecretIsRejectedIncludingWithSurroundingWhitespace() {
        assertEquals(2, JwtSecretPolicy.PUBLISHED_SECRET_SHA256.size());
        String formerDefault = "change-me-local-development-secret-32chars";
        String formerGatewayTestFixture = "0123456789abcdef".repeat(2);
        for (String published : new String[] {formerDefault, formerGatewayTestFixture}) {
            assertTrue(JwtSecretPolicy.isPublished(published));
            assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong(published));
            assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong(" " + published + " "));
        }
    }

    @Test
    void repetitiveSecretFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong("a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> JwtSecretPolicy.requireStrong("abcd".repeat(16)));
    }
}
