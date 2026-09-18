package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class OAuthProviderRegistry {
    private final Environment environment;

    public OAuthProviderRegistry(Environment environment) {
        this.environment = environment;
    }

    public ProviderOAuthConfig config(ConnectorProvider provider) {
        return switch (provider) {
            case GITHUB -> new ProviderOAuthConfig(
                    provider,
                    required("AETHERIS_OAUTH_GITHUB_CLIENT_ID"),
                    required("AETHERIS_OAUTH_GITHUB_CLIENT_SECRET"),
                    value("AETHERIS_OAUTH_GITHUB_AUTH_URL", "https://github.com/login/oauth/authorize"),
                    value("AETHERIS_OAUTH_GITHUB_TOKEN_URL", "https://github.com/login/oauth/access_token"),
                    value("AETHERIS_OAUTH_GITHUB_USERINFO_URL", "https://api.github.com/user"),
                    List.of("read:user", "user:email"),
                    true
            );
            case GMAIL -> google(provider, List.of(
                    "openid", "email", "profile", "https://www.googleapis.com/auth/gmail.readonly"));
            case CALENDAR -> google(provider, List.of(
                    "openid", "email", "profile", "https://www.googleapis.com/auth/calendar.readonly"));
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OAuth live-provider foundation is not enabled for provider: " + provider);
        };
    }

    private ProviderOAuthConfig google(ConnectorProvider provider, List<String> scopes) {
        return new ProviderOAuthConfig(
                provider,
                required("AETHERIS_OAUTH_GOOGLE_CLIENT_ID"),
                required("AETHERIS_OAUTH_GOOGLE_CLIENT_SECRET"),
                value("AETHERIS_OAUTH_GOOGLE_AUTH_URL", "https://accounts.google.com/o/oauth2/v2/auth"),
                value("AETHERIS_OAUTH_GOOGLE_TOKEN_URL", "https://oauth2.googleapis.com/token"),
                value("AETHERIS_OAUTH_GOOGLE_USERINFO_URL", "https://openidconnect.googleapis.com/v1/userinfo"),
                scopes,
                true
        );
    }

    private String required(String key) {
        String result = environment.getProperty(key);
        if (result == null || result.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Provider OAuth runtime configuration is incomplete: " + key);
        }
        return result.trim();
    }

    private String value(String key, String fallback) {
        String result = environment.getProperty(key);
        return result == null || result.isBlank() ? fallback : result.trim();
    }
}
