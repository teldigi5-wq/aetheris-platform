package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S0-E6D candidate: separate audience-restricted RS256 access-token issuer.
 *
 * No HTTP endpoint invokes this bean and the existing HS256 JwtService,
 * browser login, refresh sessions and Gateway validation are unchanged.
 * This is NOT permission to issue runtime tokens to arbitrary callers.
 *
 * Until a reviewed authenticated session-bound exchange exists, this
 * capability must remain unused. Missing operator configuration = disabled;
 * incomplete/malformed configuration = fail Spring startup closed.
 */
@Component
public final class RuntimeAudienceTokenIssuer {
    private static final String PREFIX = "aetheris.runtime-token.";
    private static final long LIFETIME_SECONDS = 300;
    private static final int MAX_PKCS8_B64_LENGTH = 16384;
    private final RSAPrivateCrtKey signingKey;
    private final String keyId;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public RuntimeAudienceTokenIssuer(Environment environment) {
        this(environment, Clock.systemUTC());
    }

    RuntimeAudienceTokenIssuer(Environment environment, Clock clock) {
        Objects.requireNonNull(environment, "environment");
        this.clock = Objects.requireNonNull(clock, "clock");
        String privateKey = config(environment, PREFIX + "pkcs8-base64");
        String kid = config(environment, PREFIX + "key-id");
        String iss = config(environment, PREFIX + "issuer");
        String aud = config(environment, PREFIX + "audience");
        if (privateKey.isEmpty() && kid.isEmpty() && iss.isEmpty() && aud.isEmpty()) {
            signingKey = null;
            keyId = null;
            issuer = null;
            audience = null;
            return;
        }
        if (privateKey.isEmpty() || kid.isEmpty() || iss.isEmpty() || aud.isEmpty()
                || privateKey.length() > MAX_PKCS8_B64_LENGTH
                || !kid.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || !iss.startsWith("https://") || iss.length() > 255
                || iss.chars().anyMatch(Character::isISOControl)
                || !aud.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalStateException("Invalid Runtime audience-token operator configuration");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(privateKey);
            var parsed = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
            if (!(parsed instanceof RSAPrivateCrtKey rsa)
                    || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("Invalid RSA key");
            }
            signingKey = rsa;
        } catch (Exception invalid) {
            // Never include secret bytes or exception cause in diagnostic.
            throw new IllegalStateException("Invalid Runtime audience-token operator configuration");
        }
        keyId = kid;
        issuer = iss;
        audience = aud;
    }

    private static String config(Environment environment, String name) {
        String value = environment.getProperty(name);
        return value == null ? "" : value.strip();
    }

    public boolean isConfigured() { return signingKey != null; }

    /**
     * This method is NOT exposed by any HTTP controller, login or refresh.
     * A future server-side caller MUST independently prove authenticated,
     * nonrevoked user session and workload identity before invoking it.
     * Token identity and scopes NEVER prove tenant/object authorization.
     */
    public String issueForVerifiedExchange(IdentityAccount account) {
        if (signingKey == null) {
            throw new IllegalStateException("Runtime audience token issuance is unavailable");
        }
        if (account == null || account.getId() == null || account.getId() <= 0
                || account.getRole() == null || account.getEmail() == null
                || account.getEmail().isBlank()
                || !account.getEmail().equals(account.getEmail().strip())
                || account.getEmail().length() > 254) {
            throw new IllegalArgumentException("Invalid signed Identity account");
        }
        List<String> scopes = account.getRole().scopes().stream()
            .filter(scope -> scope.equals("orchestrator:read")
                || scope.equals("orchestrator:write"))
            .sorted().toList();
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("No allowed runtime scopes");
        }
        Instant now = clock.instant();
        return Jwts.builder()
            .header().keyId(keyId).and()
            .subject(account.getEmail())
            .issuer(issuer)
            .claim("aud", List.of(audience))
            .claim("uid", account.getId())
            .claim("role", account.getRole().name())
            .claim("scopes", scopes)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(LIFETIME_SECONDS)))
            .signWith(signingKey, Jwts.SIG.RS256)
            .compact();
    }
}
