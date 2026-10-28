package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit opt-in CI fixture, created by the *actual* Identity JwtService.
 * No private key or password is written to disk; all signed tokens are
 * synthetic and erased with the disposable CI directory.
 */
class RealIdentityInteroperabilityFixtureTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String USER_AUD = "aetheris-user-api";

    private static IdentityAccount account(Role role, long id) {
        var account = new IdentityAccount("CI Principal",
            role == Role.ADMIN ? "admin@example.invalid" : "consumer@example.invalid",
            "no-password-test-only", role);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void exportRealIdentitySignedTokensOnlyWhenExplicitCiFixturePathProvided() throws Exception {
        String filename = System.getProperty("aetheris.jwt.interop.fixture", "");
        if ("yes".equals(System.getenv("AETHERIS_INTEROP_MUST_RUN"))) {
            assertFalse(filename.isBlank(), "Explicit cross-module CI must supply a fixture path");
        }
        if (filename.isBlank()) {
            org.junit.jupiter.api.Assumptions.abort("Only executable in explicit transient CI contract step");
        }
        Path destination = Path.of(filename).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(destination.getParent()), "CI temp directory must exist");
        assertFalse(Files.exists(destination), "Never overwrite an existing fixture");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";

        // Only this in-memory signer possesses the RSA private material.
        JwtService issuer = new JwtService("", 180, ISSUER, USER_AUD,
            true, privatePem, "ci-real-issuer");
        var admin = account(Role.ADMIN, 42);
        var consumer = account(Role.API_CONSUMER, 43);
        Properties fixture = new Properties();
        fixture.setProperty("publicPemBase64",
            Base64.getEncoder().encodeToString(publicPem.getBytes(StandardCharsets.US_ASCII)));
        fixture.setProperty("issuer", ISSUER);
        fixture.setProperty("kid", "ci-real-issuer");
        fixture.setProperty("loginAdmin", issuer.issue(admin));
        fixture.setProperty("loginConsumer", issuer.issue(consumer));
        fixture.setProperty("targetUser",
            issuer.issueForApprovedAudience(admin, ApprovedTokenAudience.USER));
        fixture.setProperty("targetAudit",
            issuer.issueForApprovedAudience(admin, ApprovedTokenAudience.AUDIT));
        fixture.setProperty("targetOrchestrator",
            issuer.issueForApprovedAudience(admin, ApprovedTokenAudience.ORCHESTRATOR));
        assertEquals(8, fixture.size(), "No private signing key or credential is exported");
        Files.createFile(destination);
        try {
            try {
                Files.setPosixFilePermissions(destination, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Hosted Linux CI supports POSIX permissions.
            }
            try (var stream = Files.newOutputStream(destination)) {
                fixture.store(stream, "CI-only ephemeral real Identity JWT tokens; no private key");
            }
        } catch (Exception problem) {
            Files.deleteIfExists(destination);
            throw problem;
        }
        assertFalse(Files.readString(destination).contains("PRIVATE KEY"));
    }
}
