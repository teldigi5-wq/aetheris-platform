package io.aetheris.identity;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Reuse response for RFC 9700 section 4.14.2 (refresh token rotation).
 *
 * A revoked, unexpired refresh token presented again means the token leaked:
 * either the attacker or the legitimate client already rotated it. The server
 * cannot tell which party is calling, so it revokes every active refresh
 * session of the account and both parties must sign in again.
 *
 * REQUIRES_NEW is essential: callers such as the runtime session exchange roll
 * back on every exception, and the rejection that follows a reuse is an
 * exception. Without an independent transaction the revocation would be undone.
 * This must be a separate bean so the transactional proxy is applied.
 */
@Component
public class RefreshTokenReuseResponder {
    private final RefreshTokenRepository repository;

    public RefreshTokenReuseResponder(RefreshTokenRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllActiveSessions(Long accountId) {
        return repository.revokeAllActiveForAccount(
            Objects.requireNonNull(accountId, "accountId"), Instant.now());
    }
}
