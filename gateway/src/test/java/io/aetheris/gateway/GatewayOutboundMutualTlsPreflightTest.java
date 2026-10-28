package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No real trust material or TLS server. The native Spring Cloud Gateway SSL
 * client performs actual keystore loading/handshake in a future gated lab.
 */
class GatewayOutboundMutualTlsPreflightTest {
    private static final String USER = "https://user-service:8081";
    private static final String AUDIT = "https://audit-service:8083";
    private static final String ORCH = "http://orchestrator-service:8090";

    @TempDir Path temp;

    private Path fakeFile(String name) throws Exception {
        Path path = temp.resolve(name);
        Files.writeString(path, "CI-only non-secret placeholder; not TLS credentials");
        try {
            Files.setPosixFilePermissions(path, java.util.Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // The Linux CI runner supports this; other developer hosts may not.
        }
        return path;
    }

    private GatewayOutboundMutualTlsPreflight configured(Path key, Path ca) {
        return new GatewayOutboundMutualTlsPreflight(true, true, USER, AUDIT,
            ORCH, key.toString(), "ci-only-not-a-real-password",
            ca.toString(), "false");
    }

    @Test
    void legacyDefaultsStayUnchangedButNoHalfEnabledProfile() {
        assertDoesNotThrow(() -> new GatewayOutboundMutualTlsPreflight(
            false, false, "http://user-service:8081", "http://audit-service:8083",
            ORCH, "", "", "", "false"));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayOutboundMutualTlsPreflight(true, false,
                USER, AUDIT, ORCH, "", "", "", "false"));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayOutboundMutualTlsPreflight(false, true,
                USER, AUDIT, ORCH, "", "", "", "false"));
        assertFalse(GatewayOutboundMutualTlsPreflight.readStrictOptIn("false"));
        assertTrue(GatewayOutboundMutualTlsPreflight.readStrictOptIn("true"));
        for (String malformed : List.of("", "TRUE", "1", "yes", "true ")) {
            assertThrows(IllegalArgumentException.class,
                () -> GatewayOutboundMutualTlsPreflight.readStrictOptIn(malformed));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayOutboundMutualTlsPreflight(
                new MockEnvironment().withProperty(
                    "AETHERIS_GATEWAY_UPSTREAM_MTLS_REQUIRED", "INVALID")));
    }

    @Test
    void exactHttpsBackendsOnlyAndNoRedirectedCredentialDestinations() {
        assertTrue(GatewayOutboundMutualTlsPreflight.approvedRoute(USER, "user-service", 8081));
        assertTrue(GatewayOutboundMutualTlsPreflight.approvedRoute(AUDIT, "audit-service", 8083));
        for (String bad : List.of(
            "http://user-service:8081", "https://evil.example:8081",
            "https://user-service:8443", "https://user-service:8081/other",
            "https://user-service:8081?query=1",
            "https://attacker@user-service:8081",
            "https://user-service:8081#frag", " https://user-service:8081",
            "https://user-service:8081/../api", "not-a-url")) {
            assertFalse(GatewayOutboundMutualTlsPreflight.approvedRoute(
                bad, "user-service", 8081), bad);
        }
    }

    @Test
    void preflightRejectsUntrustedAndMissingExternalFileSettings() throws Exception {
        Path client = fakeFile("client.p12");
        Path ca = fakeFile("backend-ca.pem");
        assertDoesNotThrow(() -> configured(client, ca));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayOutboundMutualTlsPreflight(true, true,
                "http://user-service:8081", AUDIT, ORCH,
                client.toString(), "pw", ca.toString(), "false"));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayOutboundMutualTlsPreflight(true, true,
                USER, AUDIT, "https://external.invalid",
                client.toString(), "pw", ca.toString(), "false"));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayOutboundMutualTlsPreflight(true, true,
                USER, AUDIT, ORCH, client.toString(), "pw", ca.toString(), "true"));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayOutboundMutualTlsPreflight(true, true,
                USER, AUDIT, ORCH, client.toString(), "", ca.toString(), "false"));
        assertThrows(IllegalArgumentException.class,
            () -> configured(ca, ca));
        assertThrows(IllegalArgumentException.class,
            () -> configured(temp.resolve("nonexistent.p12"), ca));
        try {
            Path symlink = temp.resolve("link.p12");
            Files.createSymbolicLink(symlink, client);
            assertThrows(IllegalArgumentException.class, () -> configured(symlink, ca));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // CI Linux supports symlinks; some local platforms do not.
        }
        try {
            Files.setPosixFilePermissions(client, java.util.Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.GROUP_READ));
            assertThrows(IllegalArgumentException.class, () -> configured(client, ca));
        } catch (UnsupportedOperationException ignored) {
            // POSIX mode check applies where supported.
        }
    }
}
