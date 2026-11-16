package io.aetheris.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * S0-E6H: future transport-to-session bridge; no HTTP mapping registers it.
 *
 * A trusted TLS server connector MUST authenticate the Gateway certificate
 * chain against operator trust material with client-auth=need before this
 * code could be used by any reviewed route. Request attributes alone are
 * not a complete, tested TLS trust boundary.
 *
 * This is separate from, and never replaces, the user's revocable
 * single-use refresh credential or the Runtime's future owner authorization.
 */
@Service
public final class RuntimeAudienceWorkloadBoundExchange {
    private final RuntimeExchangeGatewayWorkloadPolicy workload;
    private final RuntimeAudienceSessionExchangeService sessions;

    public RuntimeAudienceWorkloadBoundExchange(
            RuntimeExchangeGatewayWorkloadPolicy workload,
            RuntimeAudienceSessionExchangeService sessions) {
        this.workload = Objects.requireNonNull(workload, "workload");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public RuntimeAudienceSessionExchangeService.InternalExchangeResult
            exchangeOnlyAfterVerifiedPeer(HttpServletRequest tlsRequest,
                                          String oneTimeRefreshCredential) {
        if (!workload.permits(tlsRequest)) {
            throw new RuntimeAudienceSessionExchangeService.ExchangeRejectedException();
        }
        return sessions.exchangeUsingSingleUseRefreshCredential(oneTimeRefreshCredential);
    }
}
