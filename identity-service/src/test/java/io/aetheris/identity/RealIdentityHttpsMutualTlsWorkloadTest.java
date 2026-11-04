package io.aetheris.identity;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * S0-E6I. Actual disposable Tomcat HTTPS client-auth=NEED transport proof
 * applying the real Identity workload gate to a TEST-ONLY probe servlet.
 *
 * This is NOT the real Identity Spring Boot application's full route set.
 * The internal session exchange is mocked and no token is returned.
 * It never registers a production refresh exchange or HTTP token endpoint.
 */
class RealIdentityHttpsMutualTlsWorkloadTest {
    private static final String EXPECTED_SPIFFE =
        "spiffe://aetheris.test/ns/security/sa/gateway";
    private static final String SYNTHETIC_REFRESH = "a".repeat(43);

    private static Path fixture;
    private static String fixturePassword;
    private static AnnotationConfigServletWebServerApplicationContext context;
    private static RuntimeAudienceSessionExchangeService sessions;

    @BeforeAll
    static void launchRealTlsServer() throws Exception {
        String dir = System.getProperty("aetheris.identity.tls.fixture", "");
        if ("yes".equals(System.getenv("AETHERIS_IDENTITY_TLS_LAB_MUST_RUN"))) {
            assertFalse(dir.isBlank(), "Mandatory real TLS lab fixture is absent");
        }
        Assumptions.assumeTrue(!dir.isBlank(),
            "Real Identity TLS transport only executes in disposable opt-in CI");
        fixture = Path.of(dir).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(fixture));
        assertEquals("ONLY_DISPOSABLE_E6I_CERTIFICATES",
            System.getenv("AETHERIS_IDENTITY_TLS_LAB_DISPOSABLE_MARKER"));
        fixturePassword = System.getenv("AETHERIS_IDENTITY_TLS_LAB_PASSWORD");
        assertNotNull(fixturePassword);
        assertFalse(fixturePassword.isBlank());
        for (String name : new String[]{
                "backend.p12", "ca-trust.p12", "gateway.p12",
                "wrong-client.p12", "untrusted-client.p12"}) {
            assertTrue(Files.isRegularFile(fixture.resolve(name)), name);
        }

        sessions = mock(RuntimeAudienceSessionExchangeService.class);
        when(sessions.exchangeUsingSingleUseRefreshCredential(SYNTHETIC_REFRESH))
            .thenReturn(new RuntimeAudienceSessionExchangeService.InternalExchangeResult(
                "fake-jwt-only-for-a-disposable-test",
                new RefreshTokenService.IssuedRefreshToken("rotated-test-fixture", 300L)));
        context = new AnnotationConfigServletWebServerApplicationContext();
        context.register(MinimalIdentityTlsProbe.class);
        context.refresh();
        assertTrue(context.getWebServer().getPort() > 0);
    }

    @AfterAll
    static void closeServer() {
        if (context != null) context.close();
    }

    @BeforeEach
    void resetCalls() {
        clearInvocations(sessions);
    }

    @Configuration(proxyBeanMethods = false)
    static class MinimalIdentityTlsProbe {
        @Bean
        ServletWebServerFactory realHttpsIdentityTestConnector() {
            var factory = new TomcatServletWebServerFactory(0);
            var ssl = new Ssl();
            ssl.setEnabled(true);
            ssl.setKeyStore("file:" + fixture.resolve("backend.p12"));
            ssl.setKeyStorePassword(fixturePassword);
            ssl.setKeyStoreType("PKCS12");
            ssl.setTrustStore("file:" + fixture.resolve("ca-trust.p12"));
            ssl.setTrustStorePassword(fixturePassword);
            ssl.setTrustStoreType("PKCS12");
            ssl.setClientAuth(Ssl.ClientAuth.NEED);
            factory.setSsl(ssl);
            return factory;
        }

        @Bean
        RuntimeExchangeGatewayWorkloadPolicy actualIdentityWorkloadPolicy() {
            return new RuntimeExchangeGatewayWorkloadPolicy(
                true, EXPECTED_SPIFFE, true, "need",
                fixture.resolve("ca-trust.p12").toString(), "");
        }

        @Bean
        ServletRegistrationBean<HttpServlet> testOnlyTlsProbe(
                RuntimeExchangeGatewayWorkloadPolicy policy) {
            HttpServlet servlet = new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse res)
                        throws IOException {
                    try {
                        new RuntimeAudienceWorkloadBoundExchange(policy, sessions)
                            .exchangeOnlyAfterVerifiedPeer(req, SYNTHETIC_REFRESH);
                        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    } catch (RuntimeAudienceSessionExchangeService.ExchangeRejectedException rejected) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    }
                }
            };
            var registration = new ServletRegistrationBean<HttpServlet>(
                servlet, "/__e6i-private-test-probe");
            registration.setLoadOnStartup(1);
            return registration;
        }
    }

    private static KeyStore keyStore(String file) throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        try (InputStream source = Files.newInputStream(fixture.resolve(file))) {
            store.load(source, fixturePassword.toCharArray());
        }
        return store;
    }

    private static SSLContext clientSslContext(String clientKeystore)
            throws Exception {
        var trust = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keyStore("ca-trust.p12"));
        KeyManagerFactory key = null;
        if (clientKeystore != null) {
            key = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            key.init(keyStore(clientKeystore), fixturePassword.toCharArray());
        }
        var tls = SSLContext.getInstance("TLS");
        tls.init(key == null ? null : key.getKeyManagers(),
            trust.getTrustManagers(), null);
        return tls;
    }

    private static int httpStatus(String certificate, boolean forwardedForgery)
            throws Exception {
        URL target = new URL("https://localhost:" + context.getWebServer().getPort()
            + "/__e6i-private-test-probe");
        var request = (HttpsURLConnection) target.openConnection();
        request.setSSLSocketFactory(clientSslContext(certificate).getSocketFactory());
        request.setConnectTimeout(7000);
        request.setReadTimeout(7000);
        request.setUseCaches(false);
        if (forwardedForgery) {
            request.setRequestProperty("X-Forwarded-Client-Cert", EXPECTED_SPIFFE);
            request.setRequestProperty("X-Aetheris-Verified-Uid", "1");
            request.setRequestProperty("X-Aetheris-Gateway-Identity", EXPECTED_SPIFFE);
        }
        try {
            return request.getResponseCode();
        } finally {
            request.disconnect();
        }
    }

    @Test
    void genuineTrustedGatewayTlsClientInvokesOnlyTheInternalMock() throws Exception {
        assertEquals(204, httpStatus("gateway.p12", false));
        verify(sessions, times(1))
            .exchangeUsingSingleUseRefreshCredential(SYNTHETIC_REFRESH);
    }

    @Test
    void missingTlsClientCertificateCannotBeReplacedByForwardedHeaders() {
        assertThrows(IOException.class, () -> httpStatus(null, true),
            "Tomcat client-auth NEED must reject the TLS handshake");
        verifyNoInteractions(sessions);
    }

    @Test
    void signedByTrustedCaButWrongSpiffeIsDeniedAfterRealTlsHandshake()
            throws Exception {
        assertEquals(403, httpStatus("wrong-client.p12", true));
        verifyNoInteractions(sessions);
    }

    @Test
    void clientSignedByUntrustedCaCannotReachTheServlet() {
        assertThrows(IOException.class, () -> httpStatus("untrusted-client.p12", true));
        verifyNoInteractions(sessions);
    }
}
