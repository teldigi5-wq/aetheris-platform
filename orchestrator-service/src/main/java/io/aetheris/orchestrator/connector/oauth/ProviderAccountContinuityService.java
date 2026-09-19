package io.aetheris.orchestrator.connector.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.connector.ConnectorProvider;
import io.aetheris.orchestrator.connector.action.ConnectorActionBlockedException;
import io.aetheris.orchestrator.connector.action.ConnectorActionEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ProviderAccountContinuityService {
    private static final String USER_AGENT = "Aetheris-Syntra-Connector/1.0";
    private static final int FINGERPRINT_HEX_LENGTH = 32;

    private final ProviderCredentialRepository credentials;
    private final InMemoryConnectorCredentialVault vault;
    private final OAuthProviderRegistry providers;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public ProviderAccountContinuityService(
            ProviderCredentialRepository credentials,
            InMemoryConnectorCredentialVault vault,
            OAuthProviderRegistry providers,
            ObjectMapper mapper) {
        this.credentials = credentials;
        this.vault = vault;
        this.providers = providers;
        this.mapper = mapper;
    }

    public String snapshot(UUID connectionId, ConnectorProvider expectedProvider) {
        if (connectionId == null) {
            throw new ConnectorActionBlockedException("Provider account continuity requires a connector connection");
        }
        if (expectedProvider == null) {
            throw new ConnectorActionBlockedException("Provider account continuity requires a connector provider");
        }

        ProviderCredentialEntity credential = credentials.findByConnectionId(connectionId)
                .orElseThrow(() -> new ConnectorActionBlockedException(
                        "Active OAuth credential is required to establish provider account continuity"));
        if (credential.getProvider() != expectedProvider) {
            throw new ConnectorActionBlockedException("OAuth credential provider does not match connector account continuity request");
        }
        if (credential.getStatus() != ProviderCredentialStatus.ACTIVE) {
            throw new ConnectorActionBlockedException("OAuth credential must be ACTIVE to establish provider account continuity");
        }
        if (credential.getExpiresAt() != null && Instant.now().isAfter(credential.getExpiresAt())) {
            throw new ConnectorActionBlockedException("OAuth access token is expired; refresh is required before provider account continuity can be established");
        }

        String accessToken = vault.require(credential.getAccessTokenReference());
        ProviderOAuthConfig config = providers.config(expectedProvider);
        String accountId = readStableAccountId(config, accessToken);
        return fingerprint(expectedProvider, accountId);
    }

    public void assertCurrent(ConnectorActionEntity action) {
        if (action == null) throw new IllegalArgumentException("Connector action is required");
        String expected = normalizeFingerprint(action.getAccountFingerprint());
        if (expected.isBlank()) {
            throw new ConnectorActionBlockedException(
                    "Live connector action is missing its provider account continuity fingerprint; recreate the action after reconnecting the provider");
        }
        String current = snapshot(action.getConnectionId(), action.getProvider());
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                current.getBytes(StandardCharsets.US_ASCII))) {
            throw new ConnectorActionBlockedException(
                    "Provider account changed after this live connector action was approved; recreate and re-approve the action for the current account");
        }
    }

    static String fingerprint(ConnectorProvider provider, String accountId) {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        if (accountId == null || accountId.isBlank()) {
            throw new ConnectorActionBlockedException("Provider did not return a stable account identity");
        }
        String canonical = provider.name() + "\n" + accountId.trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_HEX_LENGTH);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private String readStableAccountId(ProviderOAuthConfig config, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.userInfoEndpoint()))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ConnectorActionBlockedException("Provider account identity check was interrupted");
        } catch (IOException | IllegalArgumentException error) {
            throw new ConnectorActionBlockedException("Provider account identity could not be verified");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ConnectorActionBlockedException("Provider account identity could not be verified");
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            String accountId = config.provider() == ConnectorProvider.GITHUB
                    ? root.path("id").asText("").trim()
                    : root.path("sub").asText("").trim();
            if (accountId.isBlank()) {
                throw new ConnectorActionBlockedException("Provider did not return a stable account identity");
            }
            return accountId;
        } catch (IOException error) {
            throw new ConnectorActionBlockedException("Provider account identity response was invalid");
        }
    }

    private String normalizeFingerprint(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{32}")) return "";
        return normalized;
    }
}
