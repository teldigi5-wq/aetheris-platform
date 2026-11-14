package io.aetheris.audit.security;

import io.aetheris.audit.AuditEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Audit Spring HTTP port consumes exact Identity JwtService.issue tokens.
 * Test-only H2 with disabled AMQP listeners and a mocked event store.
 * No fake production users, external DB, or signed secret transport.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityIssuedJwtContractIT {
    @Value("${local.server.port}")
    private int port;
    @MockitoBean
    private AuditEventStore store;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    private String jwt(String kind) throws Exception {
        Path dir=Path.of("..","identity-service","target","identity-jwt-contract")
            .toAbsolutePath().normalize();
        assertEquals("IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1",
            Files.readString(dir.resolve("test-only.marker"),StandardCharsets.US_ASCII).trim());
        return Files.readString(dir.resolve(kind+".jwt"),StandardCharsets.US_ASCII).trim();
    }

    private int get(String token, boolean forged) throws Exception {
        assertTrue(port>0);
        HttpRequest.Builder req=HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:"+port+"/api/events"))
            .timeout(Duration.ofSeconds(8));
        if(token!=null) req.header("Authorization","Bearer "+token);
        if(forged) {
            req.header("X-Aetheris-User-Role","ADMIN");
            req.header("X-Aetheris-Scopes","services:read services:write");
        }
        return http.send(req.GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void actualIdentityAdminJwtIsAcceptedOnRealAuditHttp() throws Exception {
        assertEquals(200,get(jwt("admin"),false));
    }

    @Test
    void actualIdentityNonadminJwtIsForbiddenEvenWithSpoofedAdminHeaders() throws Exception {
        assertEquals(403,get(jwt("consumer"),true));
        assertEquals(403,get(jwt("developer"),true));
        assertEquals(401,get(null,true));
    }

    @Test
    void actualIdentityExpiredAndTamperedJwtAreUnauthorized() throws Exception {
        assertEquals(401,get(jwt("expired"),false));
        assertEquals(401,get(jwt("tampered"),false));
    }
}
