package io.aetheris.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * S0-E6F INTERNAL session-bound token exchange building block.
 *
 * There is NO HTTP caller for this service. Never expose this method from
 * AuthController/BrowserAuthController until independent browser CSRF,
 * workload mTLS and service authorization are reviewed. Possession of a
 * refresh credential alone does not authenticate a Gateway workload.
 *
 * The presented refresh token is atomically claimed and replaced by the
 * existing RefreshTokenService.rotate() mechanism, then a fresh account
 * is loaded from the database. The outer transaction rolls back rotation
 * if signing or account revalidation fails.
 *
 * Outstanding: Runtime does not introspect already issued JWTs. A logout
 * revokes refresh credentials but does not revoke an unexpired RS256 JWT.
 */
@Service
public final class RuntimeAudienceSessionExchangeService {
    private final RefreshTokenService refreshTokens;
    private final IdentityAccountRepository accounts;
    private final RuntimeAudienceTokenIssuer issuer;

    public RuntimeAudienceSessionExchangeService(
            RefreshTokenService refreshTokens,
            IdentityAccountRepository accounts,
            RuntimeAudienceTokenIssuer issuer) {
        this.refreshTokens = Objects.requireNonNull(refreshTokens, "refreshTokens");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
    }

    @Transactional(rollbackFor = Exception.class)
    public InternalExchangeResult exchangeUsingSingleUseRefreshCredential(
            String oneTimeRefreshCredential) {
        if (!issuer.isConfigured()) {
            throw new ExchangeRejectedException();
        }
        // Existing refresh credential issuer produces precisely 32 random
        // bytes encoded as unpadded base64url (43 ASCII characters).
        // Reject arbitrary input before passing it to the hash+DB lookup.
        if (oneTimeRefreshCredential == null
                || !oneTimeRefreshCredential.matches("[A-Za-z0-9_-]{43}")) {
            throw new ExchangeRejectedException();
        }

        final RefreshTokenService.RotationResult rotation;
        try {
            rotation = refreshTokens.rotate(oneTimeRefreshCredential);
        } catch (RefreshTokenService.InvalidRefreshTokenException denied) {
            // Hide whether token was unknown, reused, expired or revoked.
            throw new ExchangeRejectedException();
        }

        IdentityAccount sessionAccount = rotation.account();
        Long accountId = sessionAccount == null ? null : sessionAccount.getId();
        if (accountId == null || accountId <= 0) {
            throw new ExchangeRejectedException();
        }
        IdentityAccount fresh = accounts.findById(accountId)
            .orElseThrow(ExchangeRejectedException::new);
        if (fresh.getId() == null || !fresh.getId().equals(accountId)
                || fresh.getRole() == null || fresh.getEmail() == null
                || fresh.getEmail().isBlank()) {
            throw new ExchangeRejectedException();
        }

        // Strictly internal; no endpoint, no bearer/session logging. This is
        // NOT object/tenant authorization, live revocation or mTLS approval.
        String audienceToken = issuer.issueForVerifiedExchange(fresh);
        return new InternalExchangeResult(
            audienceToken, rotation.refreshToken());
    }

    public record InternalExchangeResult(
        String runtimeAccessToken,
        RefreshTokenService.IssuedRefreshToken rotatedRefreshToken
    ) {
        public InternalExchangeResult {
            Objects.requireNonNull(runtimeAccessToken, "runtimeAccessToken");
            Objects.requireNonNull(rotatedRefreshToken, "rotatedRefreshToken");
        }
    }

    public static final class ExchangeRejectedException extends RuntimeException {
        public ExchangeRejectedException() {
            super("Runtime session exchange denied");
        }
    }
}
