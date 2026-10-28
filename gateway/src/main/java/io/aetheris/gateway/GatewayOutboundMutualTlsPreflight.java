package io.aetheris.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Startup-only safety gate for the Gateway's opt-in outbound TLS profile.
 * Spring Cloud Gateway's native Reactor Netty SSL properties own the actual
 * certificate loading and handshake; this does NOT attest a live mTLS peer.
 */
@Component
public final class GatewayOutboundMutualTlsPreflight {
    @Autowired
    public GatewayOutboundMutualTlsPreflight(Environment environment) {
        this(Boolean.parseBoolean(environment.getProperty(
                "AETHERIS_GATEWAY_UPSTREAM_MTLS_REQUIRED", "false")),
            environment.acceptsProfiles(Profiles.of("gateway-mtls")),
            environment.getProperty("AETHERIS_USER_BACKEND_URI",
                "http://user-service:8081"),
            environment.getProperty("AETHERIS_AUDIT_BACKEND_URI",
                "http://audit-service:8083"),
            environment.getProperty("AETHERIS_GATEWAY_TLS_CLIENT_KEY_STORE_PATH", ""),
            environment.getProperty("AETHERIS_GATEWAY_TLS_CLIENT_KEY_STORE_PASSWORD", ""),
            environment.getProperty("AETHERIS_GATEWAY_TLS_TRUSTED_CA_CERT_PATH", ""),
            environment.getProperty(
                "spring.cloud.gateway.httpclient.ssl.use-insecure-trust-manager",
                "false"));
    }

    GatewayOutboundMutualTlsPreflight(boolean required, boolean profile,
            String userUri, String auditUri, String keyStorePath,
            String keyStorePassword, String caCertPath, String insecureTrust) {
        if (!required && !profile) {
            // Preserve existing local development with unchanged HTTP routes.
            return;
        }
        if (!required || !profile) {
            throw new IllegalArgumentException(
                "Gateway mTLS needs both explicit opt-in and gateway-mtls profile");
        }
        if (!approvedRoute(userUri, "user-service", 8081)
                || !approvedRoute(auditUri, "audit-service", 8083)) {
            throw new IllegalArgumentException(
                "Gateway mTLS requires exact HTTPS User and Audit upstream identities");
        }
        if (!"false".equalsIgnoreCase(Objects.toString(insecureTrust, ""))) {
            throw new IllegalArgumentException("Insecure Gateway TLS trust is forbidden");
        }
        if (keyStorePassword == null || keyStorePassword.isBlank()) {
            throw new IllegalArgumentException(
                "Gateway mTLS needs a separately provisioned client key-store password");
        }
        requireExternalFile(keyStorePath, "client keystore");
        requireExternalFile(caCertPath, "backend CA certificate");
        if (Path.of(keyStorePath).normalize().equals(Path.of(caCertPath).normalize())) {
            throw new IllegalArgumentException("Client keystore and CA paths must differ");
        }
    }

    static boolean approvedRoute(String route, String hostname, int port) {
        if (route == null || route.isBlank() || !route.equals(route.trim())) return false;
        try {
            URI uri = URI.create(route);
            return !uri.isOpaque()
                && "https".equals(uri.getScheme())
                && hostname.equals(uri.getHost())
                && port == uri.getPort()
                && uri.getUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
        } catch (IllegalArgumentException badRoute) {
            return false;
        }
    }

    private static void requireExternalFile(String value, String description) {
        if (value == null || !value.startsWith("/") || !value.equals(value.trim())) {
            throw new IllegalArgumentException(
                "Gateway mTLS needs an absolute external path for " + description);
        }
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute() || !path.normalize().equals(path)
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(path) || Files.size(path) == 0) {
                throw new IllegalArgumentException(
                    "Gateway mTLS " + description + " is missing or invalid");
            }
        } catch (java.io.IOException | java.nio.file.InvalidPathException failed) {
            throw new IllegalArgumentException(
                "Gateway mTLS " + description + " cannot be read");
        }
    }
}
