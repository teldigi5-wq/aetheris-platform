package io.aetheris.users.security;

import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Optional verified client-certificate workload boundary. Never trusts HTTP
 * headers for workload identity. The servlet container MUST first authenticate
 * the peer certificate chain against an operator-controlled trust anchor.
 */
final class GatewayMutualTlsWorkloadPolicy {
    private static final String CLIENT_CERT_ATTRIBUTE =
        "jakarta.servlet.request.X509Certificate";
    private static final String CLIENT_AUTH_EKU = "1.3.6.1.5.5.7.3.2";
    private final boolean required;
    private final String expectedSpiffeId;

    GatewayMutualTlsWorkloadPolicy(boolean required, String expectedSpiffeId,
            boolean tlsEnabled, String clientAuth, String trustStore, String sslBundle) {
        this.required = required;
        this.expectedSpiffeId = expectedSpiffeId;
        if (!required) {
            if (hasText(expectedSpiffeId)) {
                throw new IllegalArgumentException(
                    "Gateway SPIFFE policy configured without enabling workload mTLS");
            }
            return;
        }
        // Reject configuration that could accept proxy headers, TLS-optional
        // client certificates, or the JVM's broad default public CA anchors.
        if (!tlsEnabled || !"need".equals(clientAuth)
                || (!hasText(trustStore) && !hasText(sslBundle))) {
            throw new IllegalArgumentException(
                "Gateway workload identity requires server TLS, client-auth=need and explicit trust material");
        }
        if (!validSpiffeId(expectedSpiffeId)) {
            throw new IllegalArgumentException("Gateway workload SPIFFE ID is invalid");
        }
    }

    boolean required() { return required; }

    boolean permits(HttpServletRequest request) {
        if (!required) return true;
        Object attribute = request.getAttribute(CLIENT_CERT_ATTRIBUTE);
        if (!(attribute instanceof X509Certificate[] certificates)
                || certificates.length == 0 || certificates[0] == null) return false;
        X509Certificate leaf = certificates[0];
        try {
            leaf.checkValidity(new Date());
            // Do not treat a CA certificate or a server-only certificate as a
            // client workload identity. Reject missing clientAuth EKU.
            if (leaf.getBasicConstraints() >= 0) return false;
            List<String> keyUses = leaf.getExtendedKeyUsage();
            if (keyUses == null || !keyUses.contains(CLIENT_AUTH_EKU)) return false;
            boolean[] usage = leaf.getKeyUsage();
            if (usage == null || usage.length == 0 || !usage[0]) return false;
            Collection<List<?>> names = leaf.getSubjectAlternativeNames();
            if (names == null) return false;
            String onlyUri = null;
            for (List<?> name : names) {
                if (name == null || name.size() < 2) continue;
                if (!(name.get(0) instanceof Integer type) || type != 6) continue;
                if (!(name.get(1) instanceof String uri) || onlyUri != null) return false;
                onlyUri = uri;
            }
            return expectedSpiffeId.equals(onlyUri);
        } catch (CertificateException | IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank() && s.equals(s.trim());
    }

    static boolean validSpiffeId(String id) {
        return id != null && id.length() <= 250
            && id.matches("spiffe://[a-z0-9]([a-z0-9.-]{0,125}[a-z0-9])?/ns/[a-z0-9]([-a-z0-9]*[a-z0-9])?/sa/[a-z0-9]([-a-z0-9]*[a-z0-9])?");
    }
}
