package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;
    private final String issuer;
    private final String audience;
    private final boolean targetedIssuance;

    @Autowired
    public JwtService(@Value("${aetheris.jwt.secret}") String secret,
                      @Value("${aetheris.jwt.expiration-seconds:3600}") long expirationSeconds,
                      @Value("${AETHERIS_IDENTITY_JWT_ISSUER:}") String issuer,
                      @Value("${AETHERIS_IDENTITY_JWT_AUDIENCE:}") String audience) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        if (expirationSeconds <= 0 || expirationSeconds > 3600) {
            throw new IllegalArgumentException("Access token lifetime must be within 1..3600 seconds");
        }
        boolean anyPolicySet = (issuer != null && !issuer.isEmpty())
            || (audience != null && !audience.isEmpty());
        if (anyPolicySet && (!validTarget(issuer, 255) || !validTarget(audience, 128))) {
            // An incomplete or malformed operator setting must NEVER silently
            // downgrade issuance back to untargeted legacy JWTs.
            throw new IllegalArgumentException("Identity issuer/audience policy incomplete or invalid");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
        this.audience = audience;
        this.targetedIssuance = anyPolicySet;
    }

    // Test-only compatibility constructor. Production Spring autowires all four.
    JwtService(String secret, long expirationSeconds) {
        this(secret, expirationSeconds, "", "");
    }

    private static boolean validTarget(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
            && value.equals(value.trim());
    }

    public String issue(IdentityAccount account) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(account.getEmail())
                .claim("uid", account.getId())
                .claim("name", account.getName())
                .claim("role", account.getRole().name())
                .claim("scopes", account.getRole().scopes().stream().sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)));
        if (targetedIssuance) {
            // Exactly one intended backend: this token cannot be a general
            // permission grant to ALL gateway routes or all services.
            builder.issuer(issuer).claim("aud", java.util.List.of(audience));
        }
        return builder.signWith(key).compact();
    }

    public long expirationSeconds() { return expirationSeconds; }
}
