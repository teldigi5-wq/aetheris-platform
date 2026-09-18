package io.aetheris.orchestrator.connector.action;

/**
 * Represents a real provider HTTP rejection after Aetheris has passed its own
 * approval, policy, credential, and action-state gates.
 *
 * <p>The provider response body is deliberately discarded. Only the numeric
 * upstream status is retained so diagnostics cannot leak provider data.</p>
 */
public class LiveProviderWriteException extends RuntimeException {
    private final int providerStatus;

    public LiveProviderWriteException(int providerStatus) {
        super("Live provider write failed with HTTP " + providerStatus);
        this.providerStatus = providerStatus;
    }

    public int getProviderStatus() {
        return providerStatus;
    }
}
