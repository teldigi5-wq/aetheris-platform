package io.aetheris.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S0-E6H prospective connector-side certificate checks only.
 *
 * Mock servlet certificates do NOT prove a real CA-validated mTLS handshake.
 * No customer route, key material or production TLS listener is started.
 */
class RuntimeExchangeGatewayWorkloadPolicyTest {
    private static final String GATEWAY =
        "spiffe://aetheris.test/ns/security/sa/gateway";

    private static RuntimeExchangeGatewayWorkloadPolicy enabled() {
        return new RuntimeExchangeGatewayWorkloadPolicy(
            true, GATEWAY, true, "need", "operator-ca-truststore.p12", "");
    }

    private static MockHttpServletRequest https() {
        var req = new MockHttpServletRequest("POST", "/__unregistered-runtime-exchange");
        req.setSecure(true);
        req.setScheme("https");
        return req;
    }

    private static X509Certificate peer(String spiffe) throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getBasicConstraints()).thenReturn(-1);
        when(cert.getKeyUsage()).thenReturn(new boolean[]{true});
        when(cert.getExtendedKeyUsage()).thenReturn(
            List.of(RuntimeExchangeGatewayWorkloadPolicy.CLIENT_AUTH_EKU));
        when(cert.getSubjectAlternativeNames()).thenReturn(
            List.of(List.of(6, spiffe)));
        return cert;
    }

    @Test
    void disabledAndMissingCertificateFailClosedBeforeAnySessionAccess() {
        var disabled = new RuntimeExchangeGatewayWorkloadPolicy(
            false, "", false, "", "", "");
        assertFalse(disabled.isEnabled());
        assertFalse(disabled.permits(https()));
        var sessions = mock(RuntimeAudienceSessionExchangeService.class);
        var adapter = new RuntimeAudienceWorkloadBoundExchange(disabled, sessions);
        var request = https();
        request.addHeader("X-Forwarded-Client-Cert", GATEWAY);
        request.addHeader("X-Aetheris-Gateway-Identity", GATEWAY);
        assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> adapter.exchangeOnlyAfterVerifiedPeer(request, "not-a-real-token"));
        verifyNoInteractions(sessions);
        assertFalse(enabled().permits(request));
    }

    @Test
    void invalidTlsOrSpiffeOperatorConfigurationCannotEnableExchange() {
        assertThrows(IllegalStateException.class, () ->
            new RuntimeExchangeGatewayWorkloadPolicy(
                false, GATEWAY, false, "", "", ""));
        assertThrows(IllegalStateException.class, () ->
            new RuntimeExchangeGatewayWorkloadPolicy(
                true, GATEWAY, false, "need", "truststore.p12", ""));
        assertThrows(IllegalStateException.class, () ->
            new RuntimeExchangeGatewayWorkloadPolicy(
                true, GATEWAY, true, "want", "truststore.p12", ""));
        assertThrows(IllegalStateException.class, () ->
            new RuntimeExchangeGatewayWorkloadPolicy(
                true, GATEWAY, true, "need", "", ""));
        assertThrows(IllegalStateException.class, () ->
            new RuntimeExchangeGatewayWorkloadPolicy(
                true, "spiffe://invalid/../../gateway", true, "need", "ca.p12", ""));
        assertThrows(IllegalStateException.class, () ->
            new RuntimeExchangeGatewayWorkloadPolicy(
                true, GATEWAY, true, "need", " not-a-real-path ", ""));
        assertTrue(RuntimeExchangeGatewayWorkloadPolicy.validSpiffeId(GATEWAY));
        assertFalse(RuntimeExchangeGatewayWorkloadPolicy.validSpiffeId(
            "https://gateway.invalid"));
    }

    @Test
    void exactSyntheticPeerAndHttpsPermitOnlyTheInternalAdapter() throws Exception {
        var sessions = mock(RuntimeAudienceSessionExchangeService.class);
        var replacement = new RefreshTokenService.IssuedRefreshToken(
            "synthetic-rotated-test-secret", 300);
        var exchangeResult = new RuntimeAudienceSessionExchangeService.InternalExchangeResult(
            "synthetic-runtime-jwt", replacement);
        String refresh = "a".repeat(43);
        when(sessions.exchangeUsingSingleUseRefreshCredential(refresh))
            .thenReturn(exchangeResult);
        var request = https();
        request.setAttribute(RuntimeExchangeGatewayWorkloadPolicy.PEER_CERTIFICATE_ATTRIBUTE,
            new X509Certificate[]{peer(GATEWAY)});
        var bound = new RuntimeAudienceWorkloadBoundExchange(enabled(), sessions);
        assertSame(exchangeResult,
            bound.exchangeOnlyAfterVerifiedPeer(request, refresh));
        verify(sessions, times(1)).exchangeUsingSingleUseRefreshCredential(refresh);
        assertFalse(exchangeResult.toString().contains("synthetic-runtime-jwt"));
        assertFalse(exchangeResult.toString().contains("synthetic-rotated-test-secret"));
    }

    @Test
    void untrustedSyntheticPeersAndForwardedHeadersCannotReachSessions()
            throws Exception {
        var sessions = mock(RuntimeAudienceSessionExchangeService.class);
        var adapter = new RuntimeAudienceWorkloadBoundExchange(enabled(), sessions);
        var candidates = List.of(
            peer("spiffe://aetheris.test/ns/security/sa/attacker"),
            peer("spiffe://untrusted.invalid/ns/security/sa/gateway")
        );
        for (var candidate : candidates) {
            var request = https();
            request.addHeader("X-Forwarded-Client-Cert", GATEWAY);
            request.addHeader("X-Aetheris-Gateway-Identity", GATEWAY);
            request.setAttribute(
                RuntimeExchangeGatewayWorkloadPolicy.PEER_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[]{candidate});
            assertThrows(RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
                () -> adapter.exchangeOnlyAfterVerifiedPeer(request, "a".repeat(43)));
        }
        // Use an explicit servlet mock for this condition: Spring's
        // MockHttpServletRequest derives secure=true from the https scheme,
        // so setSecure(false) alone is not an accurate insecure transport.
        HttpServletRequest notHttps = mock(HttpServletRequest.class);
        when(notHttps.isSecure()).thenReturn(false);
        when(notHttps.getScheme()).thenReturn("https");
        assertFalse(enabled().permits(notHttps));
        var notHttpsScheme = https();
        notHttpsScheme.setScheme("http");
        notHttpsScheme.setAttribute(
            RuntimeExchangeGatewayWorkloadPolicy.PEER_CERTIFICATE_ATTRIBUTE,
            new X509Certificate[]{peer(GATEWAY)});
        assertFalse(enabled().permits(notHttpsScheme));
        verifyNoInteractions(sessions);
    }

    @Test
    void expiredCaServerOnlyAndAmbiguousSanCannotImpersonateGateway()
            throws Exception {
        X509Certificate expired = peer(GATEWAY);
        doThrow(new CertificateExpiredException("synthetic-expired"))
            .when(expired).checkValidity(any(Date.class));
        X509Certificate ca = peer(GATEWAY);
        when(ca.getBasicConstraints()).thenReturn(0);
        X509Certificate serverOnly = peer(GATEWAY);
        when(serverOnly.getExtendedKeyUsage()).thenReturn(
            List.of("1.3.6.1.5.5.7.3.1"));
        X509Certificate noDigitalSignature = peer(GATEWAY);
        when(noDigitalSignature.getKeyUsage()).thenReturn(new boolean[]{false});
        X509Certificate ambiguous = peer(GATEWAY);
        when(ambiguous.getSubjectAlternativeNames()).thenReturn(
            List.of(List.of(6, GATEWAY),
                    List.of(6, "spiffe://aetheris.test/ns/security/sa/attacker")));
        X509Certificate dnsOnly = peer(GATEWAY);
        when(dnsOnly.getSubjectAlternativeNames()).thenReturn(
            List.of(List.of(2, "gateway.aetheris.invalid")));
        X509Certificate missingEku = peer(GATEWAY);
        when(missingEku.getExtendedKeyUsage()).thenReturn(null);
        for (var candidate : List.of(
                expired, ca, serverOnly, noDigitalSignature,
                ambiguous, dnsOnly, missingEku)) {
            var request = https();
            request.setAttribute(
                RuntimeExchangeGatewayWorkloadPolicy.PEER_CERTIFICATE_ATTRIBUTE,
                new X509Certificate[]{candidate});
            assertFalse(enabled().permits(request),
                "Invalid synthetic peer must never be a trusted workload");
        }
    }

    @Test
    void downstreamSessionRejectionPropagatesWithoutCredentialDisclosure()
            throws Exception {
        var sessions = mock(RuntimeAudienceSessionExchangeService.class);
        var request = https();
        request.setAttribute(RuntimeExchangeGatewayWorkloadPolicy.PEER_CERTIFICATE_ATTRIBUTE,
            new X509Certificate[]{peer(GATEWAY)});
        String credential = "b".repeat(43);
        when(sessions.exchangeUsingSingleUseRefreshCredential(credential))
            .thenThrow(new RuntimeAudienceSessionExchangeService.ExchangeRejectedException());
        var result = assertThrows(
            RuntimeAudienceSessionExchangeService.ExchangeRejectedException.class,
            () -> new RuntimeAudienceWorkloadBoundExchange(enabled(), sessions)
                .exchangeOnlyAfterVerifiedPeer(request, credential));
        assertFalse(result.getMessage().contains(credential));
        verify(sessions, times(1)).exchangeUsingSingleUseRefreshCredential(credential);
    }
}
