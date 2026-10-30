package io.aetheris.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * S0-E6H: Identity-only, fail-closed prospective Gateway workload identity.
 *
 * Does NOT create or register any HTTP exchange endpoint. This verifier is
 * meaningful only when the actual TLS server connector validates client
 * certificate chains against a restricted operator CA and client-auth=need.
 * A request attribute alone is not trust-anchor evidence.
 *
 * Never accept X-Forwarded-Client-Cert or other caller-controlled headers.
 */
@Component
public final class RuntimeExchangeGatewayWorkloadPolicy {
    static final String PEER_CERTIFICATE_ATTRIBUTE =
        "jakarta.servlet.request.X509Certificate";
    static final String CLIENT_AUTH_EKU = "1.3.6.1.5.5.7.3.2";
    private final boolean enabled;
    private final String expectedSpiffeId;

    @Autowired
    public RuntimeExchangeGatewayWorkloadPolicy(
        @Value("\${aetheris.runtime-exchange.gateway-mtls-required:false}") boolean enabled,
        @Value("\${aetheris.runtime-exchange.gateway-spiffe-id:}") String expectedSpiffeId,
        @Value("\${server.ssl.enabled:false}") boolean tlsEnabled,
        @Value("\${server.ssl.client-auth:}") String clientAuth,
        @Value("\${server.ssl.trust-store:}") String trustStore,
        @Value("\${server.ssl.bundle:}") String sslBundle) {
        this.enabled = enabled;
        this.expectedSpiffeId = expectedSpiffeId;
        boolean spiffeSupplied = expectedSpiffeId != null && !expectedSpiffeId.isEmpty();
        if (!enabled) {
            if (spiffeSupplied) {
                throw new IllegalStateException("Incomplete Gateway workload configuration");
            }
            return;
        }
        if (!tlsEnabled || !"need".equals(clientAuth)
                || !(nonBlank(trustStore) || nonBlank(sslBundle))
                || !validSpiffeId(expectedSpiffeId)) {
            throw new IllegalStateException(
                "Runtime token exchange requires explicit TLS mutual-auth and pinned Gateway SPIFFE");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Verifies certificate identity constraints on an HTTPS servlet request
     * already authenticated by a trusted TLS container.
     *
     * A synthetic test request can imitate servlet attributes; actual TLS
     * handshake/CA validation must be separately verified before release.
     */
    boolean permits(HttpServletRequest request) {
        if (!enabled || request == null || !request.isSecure()
                || !"https".equalsIgnoreCase(request.getScheme())) {
            return false;
        }
        Object raw = request.getAttribute(PEER_CERTIFICATE_ATTRIBUTE);
        if (!(raw instanceof X509Certificate[] certificates)
                || certificates.length == 0 || certificates[0] == null) {
            return false;
        }
        X509Certificate peer = certificates[0];
        try {
            peer.checkValidity(new Date());
            if (peer.getBasicConstraints() >= 0) return false;
            boolean[] keyUsage = peer.getKeyUsage();
            if (keyUsage == null || keyUsage.length == 0 || !keyUsage[0]) return false;
            List<String> eku = peer.getExtendedKeyUsage();
            if (eku == null || !eku.contains(CLIENT_AUTH_EKU)) return false;
            Collection<List<?>> names = peer.getSubjectAlternativeNames();
            if (names == null || names.size() != 1) return false;
            List<?> only = names.iterator().next();
            return only != null && only.size() == 2
                && Objects.equals(only.get(0), Integer.valueOf(6))
                && expectedSpiffeId.equals(only.get(1));
        } catch (CertificateException | IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank() && value.equals(value.strip());
    }

    static boolean validSpiffeId(String value) {
        return value != null && value.length() <= 250
            && value.matches(
                "spiffe://[a-z0-9]([a-z0-9.-]{0,125}[a-z0-9])?/ns/[a-z0-9]([-a-z0-9]*[a-z0-9])?/sa/[a-z0-9]([-a-z0-9]*[a-z0-9])?");
    }
}
