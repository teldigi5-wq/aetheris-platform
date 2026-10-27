package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final PrivateKey rs256PrivateKey;
    private final String rs256KeyId;
    private final long expirationSeconds;
    private final String issuer;
    private final String audience;
    private final boolean targetedIssuance;
    private final boolean approvedTargetIssuanceEnabled;

    @Autowired
    public JwtService(@Value("${aetheris.jwt.secret}") String secret,
                      @Value("${aetheris.jwt.expiration-seconds:3600}") long expirationSeconds,
                      @Value("${AETHERIS_IDENTITY_JWT_ISSUER:}") String issuer,
                      @Value("${AETHERIS_IDENTITY_JWT_AUDIENCE:}") String audience,
                      @Value("${AETHERIS_IDENTITY_TARGETED_TOKENS_ENABLED:false}")
                      boolean approvedTargetIssuanceEnabled,
                      @Value("${AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM:}")
                      String rs256Pem,
                      @Value("${AETHERIS_IDENTITY_JWT_RS256_KEY_ID:}") String rs256KeyId) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("Access token lifetime must be positive");
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
        // A configured private key selects RS256 exclusively. Any malformed
        // PEM fails startup rather than falling back to shared HMAC issuance.
        if (rs256Pem != null && !rs256Pem.isEmpty() && !anyPolicySet) {
            throw new IllegalArgumentException("RS256 issuance requires issuer and audience");
        }
        if (rs256KeyId != null && !rs256KeyId.isEmpty() &&
            ((rs256Pem == null || rs256Pem.isEmpty()) ||
             !rs256KeyId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) {
            throw new IllegalArgumentException("RS256 key ID requires reviewed private signing key");
        }
        this.rs256KeyId = rs256KeyId == null || rs256KeyId.isEmpty() ? null : rs256KeyId;
        this.rs256PrivateKey = rs256Pem == null || rs256Pem.isEmpty()
            ? null : JwtRs256KeySupport.privateKey(rs256Pem);
        // The service cannot run with target minting switched on but an
        // incomplete issuer+audience policy. Refuse to initialize.
        if (approvedTargetIssuanceEnabled && !anyPolicySet) {
            throw new IllegalArgumentException("Targeted issuance needs reviewed issuer/audience policy");
        }
        this.approvedTargetIssuanceEnabled = approvedTargetIssuanceEnabled;
    }

    // Test-only compatibility constructors. Production Spring autowires six.
    JwtService(String secret, long expirationSeconds) {
        this(secret, expirationSeconds, "", "", false, "", "");
    }

    JwtService(String secret, long expirationSeconds, String issuer, String audience) {
        this(secret, expirationSeconds, issuer, audience, false, "", "");
    }

    JwtService(String secret, long expirationSeconds, String issuer, String audience,
               boolean approvedTargetIssuanceEnabled) {
        this(secret, expirationSeconds, issuer, audience, approvedTargetIssuanceEnabled, "", "");
    }

    JwtService(String secret, long expirationSeconds, String issuer, String audience,
               boolean enableTarget, String privatePem) {
        this(secret, expirationSeconds, issuer, audience, enableTarget, privatePem, "");
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
        if (rs256PrivateKey != null && rs256KeyId != null) {
            builder.header().keyId(rs256KeyId).and();
        }
        return (rs256PrivateKey == null
                ? builder.signWith(key) : builder.signWith(rs256PrivateKey, Jwts.SIG.RS256))
                .compact();
    }

    /**
     * NOT exposed through a controller or public token exchange. A future
     * authenticated exchange must independently validate client and subject,
     * tenant/object grants and revocation before invoking this method.
     * Existing caller-supplied target strings, URLs, and headers are NOT
     * accepted; the target must be an approved Java enum constant.
     */
    String issueForApprovedAudience(IdentityAccount account, ApprovedTokenAudience target) {
        if (!approvedTargetIssuanceEnabled || !targetedIssuance) {
            throw new IllegalStateException("Target-scoped token minting is disabled");
        }
        if (account == null || target == null || account.getId() == null
                || account.getId() <= 0 || account.getEmail() == null
                || account.getEmail().isBlank() || account.getRole() == null) {
            throw new IllegalArgumentException("Verified account and target are required");
        }
        var scopes = target.allowedScopes(account.getRole());
        if (scopes.isEmpty()) {
            throw new SecurityException("Role cannot request target-scoped token");
        }
        Instant now = Instant.now();
        long cappedLifetime = Math.min(expirationSeconds, 300L);
        var builder = Jwts.builder()
                .subject(account.getEmail())
                .claim("uid", account.getId())
                .claim("role", account.getRole().name())
                .claim("scopes", scopes.stream().sorted().toList())
                .issuer(issuer)
                .claim("aud", java.util.List.of(target.audience()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(cappedLifetime)));
        if (rs256PrivateKey != null && rs256KeyId != null) {
            builder.header().keyId(rs256KeyId).and();
        }
        return (rs256PrivateKey == null
                ? builder.signWith(key) : builder.signWith(rs256PrivateKey, Jwts.SIG.RS256))
                .compact();
    }

    public long expirationSeconds() { return expirationSeconds; }
}
