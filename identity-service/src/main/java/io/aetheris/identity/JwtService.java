package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Identity is the ONLY proposed RS256 private-key holder. B59 is opt-in;
 * default hmac preserves the previously verified (but not deployed) flow.
 * Never publish PEM/PKCS8 values in logs, CI, API responses or source.
 */
@Service
public class JwtService {
    public static final String DEFAULT_ISSUER = "aetheris-identity";
    public static final String DEFAULT_AUDIENCE = "aetheris-api";
    private final SecretKey key;
    private final PrivateKey rsaPrivateKey;
    private final String rsaKid;
    private final String mode;
    private final String issuer;
    private final String audience;
    private final long expirationSeconds;

    @Autowired
    public JwtService(@Value("${aetheris.jwt.secret:}") String secret,
                      @Value("${aetheris.jwt.expiration-seconds:3600}") long expirationSeconds,
                      @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
                      @Value("${aetheris.jwt.audience:aetheris-api}") String audience,
                      @Value("${aetheris.jwt.mode:hmac}") String mode,
                      @Value("${aetheris.jwt.rsa-private-key-pkcs8-base64:}") String privateKey,
                      @Value("${aetheris.jwt.kid:}") String kid) {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must be configured");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.expirationSeconds = expirationSeconds;
        this.mode = mode;
        if ("hmac".equals(mode)) {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                    || (privateKey != null && !privateKey.isBlank())
                    || (kid != null && !kid.isBlank())) {
                throw new IllegalArgumentException("Invalid HMAC JWT signer configuration");
            }
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.rsaPrivateKey = null;
            this.rsaKid = null;
        } else if ("rs256".equals(mode)) {
            if ((secret != null && !secret.isBlank()) || privateKey == null
                    || privateKey.isBlank() || privateKey.length() > 16384
                    || kid == null || !kid.matches("[a-z][a-z0-9-]{0,30}")) {
                throw new IllegalArgumentException("Invalid RS256 JWT signer configuration");
            }
            try {
                byte[] der = Base64.getDecoder().decode(privateKey);
                PrivateKey parsed = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
                if (!(parsed instanceof RSAPrivateCrtKey rsa)
                        || rsa.getModulus().bitLength() < 3072) {
                    throw new IllegalArgumentException("RS256 private key must be RSA 3072-bit or stronger");
                }
                this.rsaPrivateKey = parsed;
            } catch (Exception invalidKey) {
                // Never log errors containing source key material.
                throw new IllegalArgumentException("Invalid RSA private signing key");
            }
            this.rsaKid = kid;
            this.key = null;
        } else {
            throw new IllegalArgumentException("Unsupported JWT signing mode");
        }
    }

    // Previous tests and pre-production HMAC integrations use this strict legacy mode.
    public JwtService(String secret, long expirationSeconds, String issuer, String audience) {
        this(secret, expirationSeconds, issuer, audience, "hmac", "", "");
    }

    public JwtService(String secret, long expirationSeconds) {
        this(secret, expirationSeconds, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    public String issue(IdentityAccount account) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(account.getEmail())
                .claim("uid", account.getId())
                .claim("name", account.getName())
                .claim("role", account.getRole().name())
                .claim("scopes", account.getRole().scopes().stream().sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)));
        if ("rs256".equals(mode)) {
            builder.header().keyId(rsaKid).and();
            return builder.signWith(rsaPrivateKey, Jwts.SIG.RS256).compact();
        }
        return builder.signWith(key).compact();
    }

    public long expirationSeconds() { return expirationSeconds; }
}
