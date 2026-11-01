package io.aetheris.users.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Real embedded Tomcat TCP/HTTP proof for the production JWT filter and
 * production controller, with ONLY downstream persistence mocked.
 * This is not a full service, production database or deployment acceptance.
 */
@SpringBootTest(
    classes = DirectApiEncodedPathRealTomcatTest.IsolatedHttpApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "server.servlet.context-path=/tenant",
        "spring.main.banner-mode=off"
    }
)
class DirectApiEncodedPathRealTomcatTest {
    private static final String TEST_SECRET = randomTestSecret();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @LocalServerPort int port;
    @Autowired io.aetheris.users.UserService downstream;

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    })
    @Import({DirectApiJwtFilter.class, io.aetheris.users.UserController.class})
    static class IsolatedHttpApp {
        @Bean
        io.aetheris.users.UserService downstream() { return mock(io.aetheris.users.UserService.class); }
    }

    @DynamicPropertySource
    static void configureTestHmac(DynamicPropertyRegistry properties) {
        properties.add("AETHERIS_JWT_SECRET", () -> TEST_SECRET);
    }

    private static String randomTestSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @BeforeEach
    void resetDownstream() {
        reset(downstream);
        when(downstream.findAll()).thenReturn(List.of());
    }

    private HttpResponse<String> send(String path, Map<String, String> headers) throws Exception {
        assertTrue(port > 0);
        HttpRequest.Builder request = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(8))
            .GET();
        for (var item : headers.entrySet()) {
            request.header(item.getKey(), item.getValue());
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String signedAuthorization() {
        Instant now = Instant.now();
        String token = Jwts.builder()
            .subject("tomcat-security-test@example.invalid")
            .claim("uid", 42L)
            .claim("role", "DEVELOPER")
            .claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)))
            .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
        return "Bearer " + token;
    }

    @Test
    void canonicalRouteRequiresJwtAndRejectsSpoofedProxyIdentity() throws Exception {
        String path = "/tenant/api/users";
        assertEquals(401, send(path, Map.of()).statusCode());
        assertEquals(401, send(path, Map.of(
            "X-Aetheris-User-Id", "42",
            "X-Aetheris-User-Role", "ADMIN",
            "X-Forwarded-User", "admin",
            "X-Original-URI", path
        )).statusCode());
        verify(downstream, never()).findAll();
    }

    @Test
    void encodedApiPrefixesMustNeverReachBusinessControllerWithoutJwt() throws Exception {
        for (String path : List.of(
            "/tenant/%61pi/users",
            "/tenant/api;v=1/users",
            "/tenant/api/users;v=1",
            "/tenant/%61pi/users;v=1"
        )) {
            int code = send(path, Map.of()).statusCode();
            // A container may reject malformed/canonicalized paths before
            // the filter, or the filter may return 401; never allow 2xx/3xx.
            assertTrue(code == 400 || code == 401 || code == 403 || code == 404,
                () -> "Encoded path escaped deny policy: " + path + " status=" + code);
            System.out.println("TOMCAT_DENIED_ENCODED_PATH status=" + code + " path=" + path);
        }
        verify(downstream, never()).findAll();
    }

    @Test
    void validSignedTokenStillReachesRealController() throws Exception {
        HttpResponse<String> response = send("/tenant/api/users",
            Map.of("Authorization", signedAuthorization()));
        assertEquals(200, response.statusCode());
        verify(downstream, times(1)).findAll();
    }
}
