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
    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long expirationSeconds;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${aetheris.jwt.refresh-expiration-seconds:604800}") long expirationSeconds) {
        this.repository = repository;
        this.expirationSeconds = expirationSeconds;
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
        RefreshToken existing = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isRevoked() || existing.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        existing.revoke();
        repository.save(existing);
        IssuedRefreshToken replacement = issue(existing.getAccount());
        return new RotationResult(existing.getAccount(), replacement);
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
