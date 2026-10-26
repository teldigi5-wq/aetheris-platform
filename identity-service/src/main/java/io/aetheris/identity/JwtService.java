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
    public static final String DEFAULT_ISSUER = "aetheris-identity";
    public static final String DEFAULT_AUDIENCE = "aetheris-api";
    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final long expirationSeconds;

    @Autowired
    public JwtService(@Value("${aetheris.jwt.secret}") String secret,
                      @Value("${aetheris.jwt.expiration-seconds:3600}") long expirationSeconds,
                      @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
                      @Value("${aetheris.jwt.audience:aetheris-api}") String audience) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    // Preserve existing isolated tests and signed-token fixtures. Not a production DI path.
    public JwtService(String secret, long expirationSeconds) {
        this(secret, expirationSeconds, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    public String issue(IdentityAccount account) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(account.getEmail())
                .claim("uid", account.getId())
                .claim("name", account.getName())
                .claim("role", account.getRole().name())
                .claim("scopes", account.getRole().scopes().stream().sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public long expirationSeconds() { return expirationSeconds; }
}
