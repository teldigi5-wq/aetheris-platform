package io.aetheris.audit.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real servlet filter and controller authorization, synthetic verified peer
 * certificate attribute. This does NOT simulate a real TLS trust handshake.
 */
class GatewayMutualTlsWorkloadPolicyTest {
    private static final String SECRET =
        "CI_REVIEW_ONLY_SYNTHETIC_JWT_HMAC_NOT_A_GATEWAY_WORKLOAD_KEY_20261028";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-audit-api";
    private static final String SPIFFE =
        "spiffe://aetheris.test/ns/security/sa/gateway";

    private static String token() {
        Instant now = Instant.now();
        return Jwts.builder().subject("admin@example.invalid")
            .claim("uid", 42L).claim("role", "ADMIN")
            .claim("scopes", List.of("services:read"))
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private static GatewayMutualTlsWorkloadPolicy enabled() {
        return new GatewayMutualTlsWorkloadPolicy(
            true, SPIFFE, true, "need", "operator-controlled-client-ca.p12", "");
    }

    private MockMvc api(io.aetheris.audit.AuditEventStore service,
                        GatewayMutualTlsWorkloadPolicy policy) {
        return MockMvcBuilders.standaloneSetup(
            new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter(SECRET, ISSUER, AUDIENCE,
                "", "", "", "", Clock.systemUTC(), policy))
            .build();
    }

    private X509Certificate certificate(String workloadIdentity) throws Exception {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getBasicConstraints()).thenReturn(-1);
        when(cert.getExtendedKeyUsage()).thenReturn(List.of("1.3.6.1.5.5.7.3.2"));
        when(cert.getKeyUsage()).thenReturn(new boolean[]{true});
        when(cert.getSubjectAlternativeNames()).thenReturn(
            List.of(List.of(6, workloadIdentity)));
        return cert;
    }

    @Test
    void disabledModePreservesExistingJwtAuthentication() throws Exception {
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        when(service.recent()).thenReturn(List.of());
        var disabled = new GatewayMutualTlsWorkloadPolicy(false, "", false, "", "", "");
        api(service, disabled).perform(get("/api/events")
            .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk());
        verify(service, times(1)).recent();
    }

    @Test
    void enabledModeRequiresVerifiedClientCertificateAndRealSignedUserJwt()
            throws Exception {
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        when(service.recent()).thenReturn(List.of());
        MockMvc api = api(service, enabled());

        api.perform(get("/api/events")
            .header("Authorization", "Bearer " + token())
            .header("X-Forwarded-Client-Cert", SPIFFE)
            .header("X-Aetheris-Gateway-Identity", SPIFFE))
            .andExpect(status().isForbidden());

        X509Certificate wrong = certificate("spiffe://aetheris.test/ns/security/sa/untrusted");
        api.perform(get("/api/events")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{wrong})
            .header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden());

        X509Certificate expired = certificate(SPIFFE);
        doThrow(new CertificateExpiredException("synthetic expired"))
            .when(expired).checkValidity(any(Date.class));
        api.perform(get("/api/events")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{expired})
            .header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden());

        X509Certificate missingClientEku = certificate(SPIFFE);
        when(missingClientEku.getExtendedKeyUsage()).thenReturn(List.of("1.3.6.1.5.5.7.3.1"));
        api.perform(get("/api/events")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{missingClientEku})
            .header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden());

        X509Certificate ambiguous = certificate(SPIFFE);
        when(ambiguous.getSubjectAlternativeNames()).thenReturn(List.of(
            List.of(6, SPIFFE), List.of(6, "spiffe://untrusted/ns/a/sa/attacker")));
        api.perform(get("/api/events")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{ambiguous})
            .header("Authorization", "Bearer " + token()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
        X509Certificate valid = certificate(SPIFFE);
        api.perform(get("/api/events")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{valid})
            .header("Authorization", "Bearer invalid-user-jwt"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        api.perform(get("/api/events")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{valid})
            .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk());
        verify(service, times(1)).recent();
    }

    @Test
    void invalidTlsTrustAndIdentitySettingsRefuseConstruction() {
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayMutualTlsWorkloadPolicy(true, SPIFFE, false, "need", "ca", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayMutualTlsWorkloadPolicy(true, SPIFFE, true, "want", "ca", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayMutualTlsWorkloadPolicy(true, SPIFFE, true, "need", "", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayMutualTlsWorkloadPolicy(true, "gateway", true, "need", "ca", ""));
        assertThrows(IllegalArgumentException.class,
            () -> new GatewayMutualTlsWorkloadPolicy(false, SPIFFE, false, "", "", ""));
        assertTrue(GatewayMutualTlsWorkloadPolicy.validSpiffeId(SPIFFE));
        assertFalse(GatewayMutualTlsWorkloadPolicy.validSpiffeId("https://example.invalid"));
        assertFalse(GatewayMutualTlsWorkloadPolicy.validSpiffeId(
            "spiffe://aetheris.test/ns/security/sa/gateway/../../admin"));
    }
}
