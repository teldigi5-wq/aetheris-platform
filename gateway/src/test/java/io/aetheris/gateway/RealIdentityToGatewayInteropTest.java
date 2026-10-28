package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The signed tokens originate in the real Identity module, NOT a test signer. */
class RealIdentityToGatewayInteropTest {
    private static final String USERS = "aetheris-user-api";
    private static final String AUDIT = "aetheris-audit-api";
    private static final String ORCH = "aetheris-orchestrator-api";

    private static Properties source() throws Exception {
        String path = System.getProperty("aetheris.jwt.interop.fixture", "");
        if ("yes".equals(System.getenv("AETHERIS_INTEROP_MUST_RUN"))) {
            assertFalse(path.isBlank(), "Explicit cross-module CI must supply a fixture path");
        }
        Assumptions.assumeTrue(!path.isBlank(), "Run only in explicit cross-module CI");
        Properties p = new Properties();
        try (var in = Files.newInputStream(Path.of(path))) { p.load(in); }
        assertEquals(8, p.size());
        return p;
    }

    private static JwtAuthenticationFilter verifier(Properties p) {
        String publicPem = new String(Base64.getDecoder().decode(
            p.getProperty("publicPemBase64")), StandardCharsets.US_ASCII);
        return new JwtAuthenticationFilter("", p.getProperty("issuer"),
            USERS, AUDIT, ORCH, publicPem, p.getProperty("kid"), "", "");
    }

    private static void assertGateway(JwtAuthenticationFilter verifier, String route,
                                      String jwt, HttpStatus rejection) {
        MockServerWebExchange request = MockServerWebExchange.from(
            MockServerHttpRequest.get(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Aetheris-Tenant-Id", "forged-tenant")
                .header("X-Aetheris-User-Role", "ADMIN")
                .header("X-Aetheris-User-Id", "90001").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex);
            return Mono.empty();
        };
        verifier.filter(request, chain).block();
        if (rejection == null) {
            assertNotNull(forwarded.get(), "Actual Gateway must forward valid principal");
            assertEquals("42", forwarded.get().getRequest().getHeaders()
                .getFirst("X-Aetheris-User-Id"));
            assertNull(forwarded.get().getRequest().getHeaders()
                .getFirst("X-Aetheris-Tenant-Id"), "Spoofed tenancy must be removed");
        } else {
            assertNull(forwarded.get(), "Denied request must not reach the downstream");
            assertEquals(rejection, request.getResponse().getStatusCode());
        }
    }

    @Test
    void realIdentityLoginTokenOnlyAllowsItsSoleDefaultUserRecipient() throws Exception {
        Properties p = source();
        JwtAuthenticationFilter gateway = verifier(p);
        assertGateway(gateway, "/api/users", p.getProperty("loginAdmin"), null);
        assertGateway(gateway, "/api/events", p.getProperty("loginAdmin"), HttpStatus.UNAUTHORIZED);
        assertGateway(gateway, "/api/orchestrator", p.getProperty("loginAdmin"),
            HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualApprovedMintTargetsAreNotInterchangeable() throws Exception {
        Properties p = source();
        JwtAuthenticationFilter gateway = verifier(p);
        assertGateway(gateway, "/api/users", p.getProperty("targetUser"), null);
        assertGateway(gateway, "/api/events", p.getProperty("targetUser"), HttpStatus.UNAUTHORIZED);
        assertGateway(gateway, "/api/events", p.getProperty("targetAudit"), null);
        assertGateway(gateway, "/api/users", p.getProperty("targetAudit"), HttpStatus.UNAUTHORIZED);
        assertGateway(gateway, "/api/orchestrator", p.getProperty("targetOrchestrator"), null);
        assertGateway(gateway, "/api/users", p.getProperty("targetOrchestrator"),
            HttpStatus.UNAUTHORIZED);
    }

    @Test
    void realConsumerLoginCannotReachPrivilegedBackendOrBecomeAdmin() throws Exception {
        Properties p = source();
        JwtAuthenticationFilter gateway = verifier(p);
        assertGateway(gateway, "/api/users", p.getProperty("loginConsumer"), HttpStatus.FORBIDDEN);
        assertGateway(gateway, "/api/events", p.getProperty("loginConsumer"),
            HttpStatus.UNAUTHORIZED);
    }
}
