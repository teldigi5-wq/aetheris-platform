package io.aetheris.gateway;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disposable REAL TCP TLS handshake using the same Reactor Netty transport
 * underlying Spring Cloud Gateway. The actual Gateway route filter and
 * backend Spring HTTP servers are NOT started or certified by this test.
 *
 * OpenSSL creates all key material only within a private temporary CI folder
 * and a shell EXIT trap removes it after this test step.
 */
class GatewayDisposableNettyMutualTlsSocketTest {
    private static final String EXPECTED_WORKLOAD =
        "spiffe://aetheris.test/ns/security/sa/gateway";
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    private static Path fixtureDir() {
        String configured = System.getProperty("aetheris.tls.lab.fixture", "");
        boolean mandatory = "yes".equals(System.getenv("AETHERIS_TLS_LAB_MUST_RUN"));
        if (mandatory) {
            assertFalse(configured.isBlank(),
                "Hosted TLS lab requires an explicit private temporary fixture");
        }
        Assumptions.assumeTrue(!configured.isBlank(),
            "Non-network Maven reactor keeps TLS socket lab opt-in");
        Path path = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(path));
        return path;
    }

    private static SslContext clientContext(Path dir, String trustCa, String clientKeyStore)
            throws Exception {
        var builder = SslContextBuilder.forClient()
            .trustManager(dir.resolve(trustCa).toFile());
        if (clientKeyStore != null && !clientKeyStore.isBlank()) {
            String password = System.getenv("AETHERIS_TLS_LAB_PASSWORD");
            assertNotNull(password, "Ephemeral test keystore password required");
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream stream = Files.newInputStream(dir.resolve(clientKeyStore))) {
                store.load(stream, password.toCharArray());
            }
            KeyManagerFactory km = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            km.init(store, password.toCharArray());
            builder.keyManager(km);
        }
        return builder.build();
    }

    private static boolean matchesGatewayPeer(X509Certificate certificate) {
        try {
            certificate.checkValidity();
            if (certificate.getBasicConstraints() >= 0) return false;
            var eku = certificate.getExtendedKeyUsage();
            if (eku == null || !eku.contains("1.3.6.1.5.5.7.3.2")) return false;
            Collection<List<?>> sans = certificate.getSubjectAlternativeNames();
            if (sans == null) return false;
            List<String> uris = new ArrayList<>();
            for (var san : sans) {
                if (san.size() >= 2 && Integer.valueOf(6).equals(san.get(0))) {
                    if (!(san.get(1) instanceof String uri)) return false;
                    uris.add(uri);
                }
            }
            return uris.size() == 1 && EXPECTED_WORKLOAD.equals(uris.get(0));
        } catch (Exception invalidCertificate) {
            return false;
        }
    }

    private static DisposableServer backend(Path dir, AtomicBoolean correctClient)
            throws Exception {
        SslContext serverSsl = SslContextBuilder.forServer(
                dir.resolve("backend.crt").toFile(), dir.resolve("backend.key").toFile())
            .trustManager(dir.resolve("ca.crt").toFile())
            .clientAuth(ClientAuth.REQUIRE)
            .build();
        // Real TLS peer certificate is provided by Netty SSLSession, not an
        // attacker-controlled X-Forwarded-Client-Cert HTTP header.
        return HttpServer.create().host("localhost").port(0)
            .secure(ssl -> ssl.sslContext(serverSsl))
            .handle((req, res) -> {
                AtomicBoolean accepted = new AtomicBoolean(false);
                req.withConnection(connection -> {
                    try {
                        SslHandler handler = connection.channel().pipeline()
                            .get(SslHandler.class);
                        var peer = handler.engine().getSession().getPeerCertificates();
                        if (peer.length == 1 || peer.length > 1) {
                            accepted.set(matchesGatewayPeer((X509Certificate) peer[0]));
                        }
                    } catch (Exception peerNotAuthenticated) {
                        accepted.set(false);
                    }
                });
                if (accepted.get()) correctClient.set(true);
                return res.status(accepted.get() ? HttpResponseStatus.OK
                    : HttpResponseStatus.FORBIDDEN)
                    .sendString(Mono.just(accepted.get() ? "MTLS_PEER_OK" : "DENIED"));
            }).bindNow(TIMEOUT);
    }

    private static int request(DisposableServer server, SslContext context,
                               boolean spoofHeader) {
        HttpClient http = HttpClient.create()
            .secure(ssl -> ssl.sslContext(context))
            .responseTimeout(Duration.ofSeconds(6));
        var req = http.headers(headers -> {
            if (spoofHeader) headers.add("X-Forwarded-Client-Cert", EXPECTED_WORKLOAD);
        }).get().uri("https://localhost:" + server.port() + "/probe");
        return req.responseSingle((resp, body) ->
            body.asString().defaultIfEmpty("").map(unused -> resp.status().code()))
            .block(TIMEOUT);
    }

    @Test
    void realNettyTcpMutualTlsAcceptsOnlyCaTrustedClientWithExactSpiffeUri()
            throws Exception {
        Path dir = fixtureDir();
        AtomicBoolean accepted = new AtomicBoolean(false);
        DisposableServer server = backend(dir, accepted);
        try {
            assertEquals(200, request(server, clientContext(dir, "ca.crt", "gateway.p12"), false));
            assertTrue(accepted.get(), "Server SSL session must expose the signed client SPIFFE SAN");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void missingClientCertificateAndForgedForwardedHeaderFailRealTlsHandshake()
            throws Exception {
        Path dir = fixtureDir();
        AtomicBoolean accepted = new AtomicBoolean(false);
        DisposableServer server = backend(dir, accepted);
        try {
            assertThrows(RuntimeException.class,
                () -> request(server, clientContextUnchecked(dir, "ca.crt", ""), true));
            assertFalse(accepted.get(), "HTTP header cannot replace a TLS client certificate");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void wrongServerCaFailsHandshakeBeforeApplicationRequest() throws Exception {
        Path dir = fixtureDir();
        AtomicBoolean accepted = new AtomicBoolean(false);
        DisposableServer server = backend(dir, accepted);
        try {
            assertThrows(RuntimeException.class,
                () -> request(server, clientContextUnchecked(dir, "untrusted-ca.crt", "gateway.p12"), false));
            assertFalse(accepted.get());
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    @Test
    void wrongSpiffeUriIsDeniedAfterCryptographicallyValidClientTlsHandshake()
            throws Exception {
        Path dir = fixtureDir();
        AtomicBoolean accepted = new AtomicBoolean(false);
        DisposableServer server = backend(dir, accepted);
        try {
            assertEquals(403,
                request(server, clientContext(dir, "ca.crt", "wrong-client.p12"), true));
            assertFalse(accepted.get(),
                "Trusted CA alone cannot authorize a different service account URI SAN");
        } finally {
            server.disposeNow(TIMEOUT);
        }
    }

    private static SslContext clientContextUnchecked(Path dir, String ca, String cert) {
        try {
            return clientContext(dir, ca, cert);
        } catch (Exception missingFixture) {
            throw new IllegalStateException(missingFixture);
        }
    }
}
