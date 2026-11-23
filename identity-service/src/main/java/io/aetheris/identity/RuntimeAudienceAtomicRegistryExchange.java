package io.aetheris.identity;

import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/**
 * S0-E6K: atomic, opt-in signed Runtime session exchange + durable ledger
 * enrollment. No Spring component and no HTTP route registers this class.
 *
 * It MUST be instantiated as a Spring-managed, transactionally proxied bean
 * only by an approved isolated integration. The inner refresh service and
 * registry JDBC MUST use exactly the same PostgreSQL transaction manager and
 * physical DataSource. A direct new(...) call is NOT transactionally safe.
 *
 * Existing Platform auth and S0-E6H internal mTLS adapter are unchanged and
 * do not auto-call this candidate. Production activation requires a separate
 * reviewed service-identity and migration plan.
 */
public class RuntimeAudienceAtomicRegistryExchange {
    private final RuntimeAudienceSessionExchangeService exchange;
    private final RuntimeAudienceTokenIssuer issuer;
    private final RuntimePg16SessionEnrollment registrar;

    public RuntimeAudienceAtomicRegistryExchange(
            RuntimeAudienceSessionExchangeService exchange,
            RuntimeAudienceTokenIssuer issuer,
            RuntimePg16SessionEnrollment registrar) {
        this.exchange = Objects.requireNonNull(exchange, "internal verified exchange");
        this.issuer = Objects.requireNonNull(issuer, "own signing key verification");
        this.registrar = Objects.requireNonNull(registrar, "same-transaction ledger");
    }

    @Transactional(rollbackFor = Exception.class)
    public RuntimeAudienceSessionExchangeService.InternalExchangeResult
            exchangeAndEnrollInternal(String oneTimeRefreshCredential) {
        // The existing service atomically claims the refresh credential,
        // reloads the persisted account and issues an audience-scoped RS256.
        var result = exchange.exchangeUsingSingleUseRefreshCredential(
            oneTimeRefreshCredential);
        // Re-verify the actual token signature/kid/issuer/audience/JTI/UID/
        // scope/expiry with the real issuer's derived public RSA key.
        var signed=issuer.verifyOwnSignedTokenForEnrollment(
            result.runtimeAccessToken());
        // Only an accepted same-transaction registry write permits a return.
        // Transactional proxy commits before exposing returned credentials.
        registrar.enroll(signed);
        return result;
    }
}
