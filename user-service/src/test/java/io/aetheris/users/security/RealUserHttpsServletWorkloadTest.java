package io.aetheris.users.security;

import io.aetheris.users.UserController;
import io.aetheris.users.UserService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Disposable, REAL embedded Tomcat HTTPS listener with client-auth=NEED and
 * the actual UserController + DirectApiJwtFilter. Not a deployed application.
 */
class RealUserHttpsServletWorkloadTest {
    static final String ISSUER = "https://identity.aetheris.example.invalid";
    static final String AUDIENCE = "aetheris-user-api";
    static final String KID = "ci-real-https";
    static final String SPIFFE = "spiffe://aetheris.test/ns/security/sa/gateway";
    static Path fixtures;
    static String password;
    static KeyPair signer;
    static AnnotationConfigServletWebServerApplicationContext context;
    static UserService mockService;

    @BeforeAll
    static void launch() throws Exception {
        String path = System.getProperty("aetheris.tls.lab.fixture", "");
        if ("yes".equals(System.getenv("AETHERIS_TLS_LAB_MUST_RUN"))) {
            assertFalse(path.isBlank(), "Required real HTTPS backend test has no fixture path");
        }
        Assumptions.assumeTrue(!path.isBlank(), "Only explicit disposable TLS CI runs live Tomcat");
        fixtures = Path.of(path).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(fixtures));
        password = System.getenv("AETHERIS_TLS_LAB_PASSWORD");
        assertNotNull(password);
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        signer = gen.generateKeyPair();
        mockService = mock(UserService.class);
        when(mockService.findAll()).thenReturn(List.of());

        context = new AnnotationConfigServletWebServerApplicationContext();
        context.register(MinimalRealHttpsUserApp.class);
        context.refresh();
        assertTrue(context.getWebServer().getPort() > 0);
    }

    @AfterAll
    static void destroy() {
        if (context != null) context.close();
    }

    static String publicPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(signer.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MinimalRealHttpsUserApp {
        @Bean
        ServletWebServerFactory actualTlsTomcat() {
            var factory = new TomcatServletWebServerFactory(0);
            var ssl = new Ssl();
            ssl.setEnabled(true);
            ssl.setKeyStore("file:" + fixtures.resolve("backend.p12"));
            ssl.setKeyStorePassword(password);
            ssl.setKeyStoreType("PKCS12");
            ssl.setTrustStore("file:" + fixtures.resolve("ca-trust.p12"));
            ssl.setTrustStorePassword(password);
            ssl.setTrustStoreType("PKCS12");
            ssl.setClientAuth(Ssl.ClientAuth.NEED);
            factory.setSsl(ssl);
            return factory;
        }

        @Bean
        DispatcherServlet dispatcherServlet() {
            return new DispatcherServlet();
        }

        @Bean
        ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistrationBean(
                DispatcherServlet servlet) {
            var registration = new ServletRegistrationBean<>(servlet, "/");
            registration.setLoadOnStartup(1);
            return registration;
        }

        @Bean
        UserService userService() {
            return mockService;
        }

        @Bean
        UserController userController(UserService service) {
            return new UserController(service);
        }

        @Bean
        DirectApiJwtFilter realUserAuthenticationFilter() {
            return new DirectApiJwtFilter("", ISSUER, AUDIENCE, publicPem(), KID,
                "", "", Clock.systemUTC(), new GatewayMutualTlsWorkloadPolicy(
                    true, SPIFFE, true, "need", "external-ci-ca-trust", ""));
        }
    }

    private static KeyStore keystore(String name) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(fixtures.resolve(name))) {
            ks.load(input, password.toCharArray());
        }
        return ks;
    }

    private static SSLContext sslContext(String clientKeystore) throws Exception {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keystore("ca-trust.p12"));
        KeyManagerFactory key = null;
        if (clientKeystore != null) {
            key = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            key.init(keystore(clientKeystore), password.toCharArray());
        }
        var ctx = SSLContext.getInstance("TLS");
        ctx.init(key == null ? null : key.getKeyManagers(), trust.getTrustManagers(), null);
        return ctx;
    }

    private static String signedToken(String role, List<String> scopes, String aud) {
        Instant now = Instant.now();
        return Jwts.builder().header().keyId(KID).and()
            .subject("ci-admin@example.invalid").claim("uid", 42L)
            .claim("role", role).claim("scopes", scopes)
            .issuer(ISSUER).claim("aud", List.of(aud))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static int status(String clientKeystore, String bearer,
            boolean forgedClientHeader) throws Exception {
        URL url = new URL("https://localhost:" + context.getWebServer().getPort() + "/api/users");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setSSLSocketFactory(sslContext(clientKeystore).getSocketFactory());
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        if (bearer != null) {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        if (forgedClientHeader) {
            connection.setRequestProperty("X-Forwarded-Client-Cert", SPIFFE);
            connection.setRequestProperty("X-Aetheris-Gateway-Identity", SPIFFE);
        }
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void matchingRealTlsClientAndPinnedIdentityJwtReachActualUserController()
            throws Exception {
        int result = status("gateway.p12",
            signedToken("ADMIN", List.of("users:read"), AUDIENCE), false);
        assertEquals(200, result);
        verify(mockService, times(1)).findAll();
    }

    @Test
    void missingClientCertRejectsTlsEvenWithForgedForwardedHeaders() {
        assertThrows(java.io.IOException.class, () -> status(null,
            signedToken("ADMIN", List.of("users:read"), AUDIENCE), true));
    }

    @Test
    void realTrustedWrongSpiffeClientAndValidJwtAreDeniedAtServletBoundary()
            throws Exception {
        assertEquals(403, status("wrong-client.p12",
            signedToken("ADMIN", List.of("users:read"), AUDIENCE), true));
    }

    @Test
    void matchingClientCertificateDoesNotBypassJwtIssuerRoleOrAudience() throws Exception {
        assertEquals(401, status("gateway.p12", "not-a-jwt", false));
        assertEquals(401, status("gateway.p12",
            signedToken("ADMIN", List.of("users:read"), "aetheris-audit-api"), false));
        assertEquals(401, status("gateway.p12",
            signedToken("API_CONSUMER", List.of("users:write"), AUDIENCE), false));
        verify(mockService, times(1)).findAll();
    }
}
