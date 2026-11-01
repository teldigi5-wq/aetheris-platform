package io.aetheris.users.security;

import io.aetheris.users.CacheConfig;
import io.aetheris.users.User;
import io.aetheris.users.UserController;
import io.aetheris.users.UserRepository;
import io.aetheris.users.UserService;
import io.aetheris.users.events.UserEventPublisher;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
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
 * Real embedded Tomcat -> production JWT filter -> real UserController ->
 * real UserService/Spring caching -> disposable Redis 7.4 over TCP.
 *
 * The repository and event publisher are synthetic mocks, never a real DB.
 * This proves deny-before-controller for currently non-entitled API consumers;
 * it does NOT implement or certify multi-tenant object ownership.
 */
@EnabledIfSystemProperty(named = "aetheris.redis.auth.http.review", matches = "true")
@SpringBootTest(
    classes = DirectApiRedisAuthorizationRealHttpTest.IsolatedHttpRedisApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "server.address=127.0.0.1",
        "server.port=0",
        "server.servlet.context-path=/tenant",
        "spring.main.banner-mode=off"
    }
)
class DirectApiRedisAuthorizationRealHttpTest {
    private static final int REDIS_PORT = 16389;
    private static final String SECRET = newSecret();
    private static final String FOREIGN_SECRET = newSecret();
    private static final String SYNTHETIC_EMAIL = "cache-boundary@example.invalid";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    })
    @Import({DirectApiJwtFilter.class, UserController.class, CacheConfig.class})
    static class IsolatedHttpRedisApp {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("127.0.0.1", REDIS_PORT);
        }

        @Bean
        UserRepository userRepository() { return mock(UserRepository.class); }

        @Bean
        UserEventPublisher publisher() { return mock(UserEventPublisher.class); }

        @Bean
        UserService userService(UserRepository repo, UserEventPublisher publisher) {
            return new UserService(repo, publisher);
        }
    }

    @DynamicPropertySource
    static void setupEphemeralJwtKey(DynamicPropertyRegistry values) {
        values.add("AETHERIS_JWT_SECRET", () -> SECRET);
    }

    @Autowired UserRepository repository;
    @Autowired CacheManager caches;
    @LocalServerPort int port;

    private static String newSecret() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    @BeforeEach
    void resetSyntheticState() {
        assertEquals("yes", System.getenv("AETHERIS_REDIS_SYNTHETIC_ONLY"),
            "Refuse non-synthetic Redis for this isolated HTTP test");
        assertNotNull(caches.getCache("usersList"));
        caches.getCache("usersList").clear();
        caches.getCache("userById").clear();
        reset(repository);
        when(repository.findAll()).thenReturn(
            List.of(new User("Synthetic cached user", SYNTHETIC_EMAIL)));
    }

    private String signed(String role, String key, String tenant) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject("synthetic-caller@example.invalid")
            .claim("uid", 42L)
            .claim("role", role)
            .claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)));
        if (tenant != null) builder.claim("tenant_id", tenant);
        return "Bearer " + builder
            .signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private HttpResponse<String> request(String method, String path,
                                         Map<String, String> headers) throws Exception {
        assertTrue(port > 0);
        var builder = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(8))
            .method(method, HttpRequest.BodyPublishers.noBody());
        headers.forEach(builder::header);
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void warmPrivilegedRedisCacheCannotBeReadUsingConsumerTenantClaimOrForgedHeaders()
            throws Exception {
        String url = "/tenant/api/users";
        String privileged = signed("DEVELOPER", SECRET, null);
        HttpResponse<String> first = request("GET", url,
            Map.of("Authorization", privileged));
        assertEquals(200, first.statusCode());
        assertTrue(first.body().contains(SYNTHETIC_EMAIL));

        HttpResponse<String> cached = request("GET", url,
            Map.of("Authorization", privileged));
        assertEquals(200, cached.statusCode());
        assertTrue(cached.body().contains(SYNTHETIC_EMAIL));
        verify(repository, times(1)).findAll();

        Map<String, String> forged = Map.of(
            "Authorization", signed("API_CONSUMER", SECRET, "tenant-alpha"),
            "X-Aetheris-Tenant-Id", "tenant-alpha",
            "X-Aetheris-User-Id", "42",
            "X-Aetheris-User-Role", "ADMIN",
            "X-Forwarded-User", "admin",
            "X-Original-URI", url
        );
        HttpResponse<String> deniedList = request("GET", url, forged);
        assertEquals(403, deniedList.statusCode());
        assertFalse(deniedList.body().contains(SYNTHETIC_EMAIL),
            "The warmed cached data must not appear in a denial response");

        HttpResponse<String> deniedObject = request("GET",
            "/tenant/api/users/7", forged);
        assertEquals(403, deniedObject.statusCode());
        assertFalse(deniedObject.body().contains(SYNTHETIC_EMAIL));

        verify(repository, times(1)).findAll();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void forgedIdentityAndForeignSignedBearerCannotReachCachedUserEndpoint()
            throws Exception {
        String url = "/tenant/api/users";
        assertEquals(401, request("GET", url, Map.of(
            "X-Aetheris-User-Role", "ADMIN",
            "X-Aetheris-User-Id", "42",
            "X-Aetheris-Tenant-Id", "tenant-alpha")).statusCode());

        assertEquals(401, request("GET", url, Map.of(
            "Authorization", signed("DEVELOPER", FOREIGN_SECRET, null))).statusCode());

        assertEquals(403, request("GET", url, Map.of(
            "Authorization", signed("API_CONSUMER", SECRET, "tenant-alpha"))).statusCode());
        verifyNoInteractions(repository);
    }
}
