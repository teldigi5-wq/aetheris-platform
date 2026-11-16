package io.aetheris.users.security;

import io.aetheris.users.UserService;
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
 * Actual User HTTP server consumes exact bytes minted by Identity's JwtService.
 * CI calls -Dtest=IdentityIssuedJwtContractIT after Identity issuer test.
 * Database is test H2, business operations mocked, no customer data touched.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.rabbitmq.listener.simple.auto-startup=false",
                "management.tracing.sampling.probability=0"})
class IdentityIssuedJwtContractIT {
    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private UserService service;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    private String jwt(String kind) throws Exception {
        Path dir=Path.of("..","identity-service","target","identity-jwt-contract")
            .toAbsolutePath().normalize();
        assertEquals("IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1",
            Files.readString(dir.resolve("test-only.marker"),StandardCharsets.US_ASCII).trim());
        return Files.readString(dir.resolve(kind+".jwt"),StandardCharsets.US_ASCII).trim();
    }

    private int request(String path, String method, String token, boolean forgedRole) throws Exception {
        assertTrue(port>0);
        HttpRequest.Builder builder=HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(forgedRole) {
            builder.header("X-Aetheris-User-Role","ADMIN");
            builder.header("X-Aetheris-Scopes","users:write users:read");
        }
        if(method.equals("DELETE")) builder.DELETE();
        else builder.GET();
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void identityAdminTokenIsAcceptedByRealUserHttpButNoRecordIsChanged() throws Exception {
        assertEquals(200,request("/api/users","GET",jwt("admin"),false));
        assertEquals(204,request("/api/users/7","DELETE",jwt("admin"),false));
    }

    @Test
    void identityConsumerAndDeveloperCannotEscalateViaForgedGatewayHints() throws Exception {
        for (String who: new String[]{"consumer","developer"}) {
            assertEquals(403,request("/api/users","GET",jwt(who),true));
            assertEquals(403,request("/api/users/7","DELETE",jwt(who),true));
        }
        assertEquals(401,request("/api/users","GET",null,true));
    }

    @Test
    void actualIdentityExpiredOrSignatureTamperedTokensAreUnauthorized() throws Exception {
        for (String kind: new String[]{"expired","tampered"}) {
            assertEquals(401,request("/api/users","GET",jwt(kind),false));
            assertEquals(401,request("/api/users/7","DELETE",jwt(kind),false));
        }
    }
}
