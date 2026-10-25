package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test-only issuer fixture: generates ACTUAL JwtService.issue() tokens.
 * Explicit CI -Dtest=IdentityIssuedJwtContractIT; not a production credential.
 * No Spring context, persistence, network, or signing algorithm reimplementation.
 */
class IdentityIssuedJwtContractIT {
    private static final String TEST_SECRET =
        "test-only-direct-backend-auth-hmac-secret-at-least-32-bytes";

    private IdentityAccount account(Role role, long id) {
        IdentityAccount account = new IdentityAccount(
            "Synthetic " + role.name(), "contract-" + role.name().toLowerCase()
                + "@example.invalid", "unused-test-only-password-hash", role);
        ReflectionTestUtils.setField(account, "id", id);
        assertEquals(id, account.getId());
        return account;
    }

    @Test
    void exportGenuineIdentityIssuedTokensForAllThreeIndependentVerifiers() throws Exception {
        Path out = Path.of("target", "identity-jwt-contract").toAbsolutePath().normalize();
        assertTrue(out.startsWith(Path.of("target").toAbsolutePath().normalize()));
        Files.createDirectories(out);
        JwtService regularIssuer = new JwtService(TEST_SECRET, 300);
        String admin = regularIssuer.issue(account(Role.ADMIN, 42));
        String developer = regularIssuer.issue(account(Role.DEVELOPER, 43));
        String consumer = regularIssuer.issue(account(Role.API_CONSUMER, 44));
        String expired = new JwtService(TEST_SECRET, -120).issue(account(Role.ADMIN, 42));

        String[] segments = admin.split("\\.");
        assertEquals(3, segments.length);
        // Explicitly corrupt a signature, never create a second signing library.
        String signedPayload = segments[0] + "." + segments[1] + ".";
        String signature = segments[2];
        String wrongSignature = (signature.startsWith("A") ? "B" : "A")
            + signature.substring(1);
        String tampered = signedPayload + wrongSignature;
        assertNotEquals(admin, tampered);

        Map<String, String> cases = Map.of(
            "admin", admin, "developer", developer, "consumer", consumer,
            "expired", expired, "tampered", tampered);
        for (var entry : cases.entrySet()) {
            String token = entry.getValue();
            assertFalse(token.contains("\n"));
            assertEquals(3, token.split("\\.").length);
            Files.writeString(out.resolve(entry.getKey() + ".jwt"), token,
                StandardCharsets.US_ASCII, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        Files.writeString(out.resolve("test-only.marker"),
            "IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1\n",
            StandardCharsets.US_ASCII, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        System.out.println("IDENTITY_REAL_ISSUER_SYNTHETIC_JWT_FIXTURES_READY roles=3 negative=2");
    }
}
