package io.aetheris.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {
    // No access-token policy bypass: refresh sessions are separately bounded
    // to at most seven days. Does not imply immediate access-JWT revocation.
    public static final long MAX_REFRESH_TOKEN_LIFETIME_SECONDS = 604800L;
    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long expirationSeconds;
    private final RefreshTokenReuseResponder reuseResponder;

    // Unit-test convenience: the responder runs without a Spring proxy.
    public RefreshTokenService(RefreshTokenRepository repository, long expirationSeconds) {
        this(repository, expirationSeconds, new RefreshTokenReuseResponder(repository));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${aetheris.jwt.refresh-expiration-seconds:604800}") long expirationSeconds,
                               RefreshTokenReuseResponder reuseResponder) {
        // Fail closed at service startup on negative, zero, or excessive
        // refresh-session validity. No request-controlled policy override.
        if (expirationSeconds < 1 || expirationSeconds > MAX_REFRESH_TOKEN_LIFETIME_SECONDS) {
            throw new IllegalArgumentException("Refresh-token lifetime must be 1..604800 seconds");
        }
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.expirationSeconds = expirationSeconds;
        this.reuseResponder = java.util.Objects.requireNonNull(reuseResponder, "reuseResponder");
    }

    @Transactional
    public IssuedRefreshToken issue(IdentityAccount account) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String tokenHash = hash(rawToken);
        repository.save(new RefreshToken(account, tokenHash, Instant.now().plusSeconds(expirationSeconds)));
        return new IssuedRefreshToken(rawToken, expirationSeconds);
    }

    @Transactional
    public RotationResult rotate(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshToken existing = repository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isExpired()) {
            throw new InvalidRefreshTokenException();
        }
        if (existing.isRevoked()) {
            // Reuse of a rotated or logged-out token: treat as leaked (RFC 9700 4.14.2).
            // Commits independently of this transaction; see RefreshTokenReuseResponder.
            Long accountId = existing.getAccount().getId();
            if (accountId != null) { // a never-persisted account owns no stored sessions
                reuseResponder.revokeAllActiveSessions(accountId);
            }
            throw new InvalidRefreshTokenException();
        }

        int claimed = repository.revokeIfActive(tokenHash, Instant.now());
        if (claimed != 1) {
            throw new InvalidRefreshTokenException();
        }

        IdentityAccount account = existing.getAccount();
        IssuedRefreshToken replacement = issue(account);
        return new RotationResult(account, replacement);
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.revoke();
            repository.save(token);
        });
    }

    public long expirationSeconds() { return expirationSeconds; }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record IssuedRefreshToken(String token, long expiresInSeconds) {}
    public record RotationResult(IdentityAccount account, IssuedRefreshToken refreshToken) {}
    public static class InvalidRefreshTokenException extends RuntimeException {}
}
