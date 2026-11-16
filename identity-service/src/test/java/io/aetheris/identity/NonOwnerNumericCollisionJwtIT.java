package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generate a signed test-only non-admin token whose uid deliberately collides
 * with the first disposable User row ID=1. No shared owner mapping exists.
 * Uses actual production Identity JwtService; no custom signing logic.
 */
class NonOwnerNumericCollisionJwtIT {
    private static final String TEST_SECRET =
        "test-only-direct-backend-auth-hmac-secret-at-least-32-bytes";

    @Test
    void exportLegitimateNonAdminIdentityJwtWhoseUidMatchesSyntheticUserRow() throws Exception {
        Path root = Path.of("target", "identity-jwt-contract").toAbsolutePath().normalize();
        assertTrue(root.startsWith(Path.of("target").toAbsolutePath().normalize()));
        assertEquals("IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1",
            Files.readString(root.resolve("test-only.marker"), StandardCharsets.US_ASCII).trim());
        JwtService issuer = new JwtService(TEST_SECRET, 300);
        for (Role role : new Role[] {Role.API_CONSUMER, Role.DEVELOPER}) {
            IdentityAccount account = new IdentityAccount(
                "Synthetic collision " + role.name(),
                "collision-" + role.name().toLowerCase() + "@example.invalid",
                "never-a-real-password-hash", role);
            ReflectionTestUtils.setField(account, "id", 1L);
            assertEquals(1L, account.getId());
            String token = issuer.issue(account);
            assertEquals(3, token.split("\\.").length);
            String filename = role == Role.API_CONSUMER
                ? "consumer-uid1.jwt" : "developer-uid1.jwt";
            Files.writeString(root.resolve(filename), token, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        System.out.println("B4Q_REAL_IDENTITY_ISSUER_NONADMIN_UID1_COLLISION_TOKENS_PREPARED");
    }
}
