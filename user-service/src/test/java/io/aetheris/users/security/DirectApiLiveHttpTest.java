package io.aetheris.users.security;

import io.aetheris.users.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Actual User service on randomized local HTTP port; test H2; mocked business
 * service to prohibit DB mutations and any Redis or RabbitMQ communications.
 * No real tokens, users or remote network calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
                "management.tracing.sampling.probability=0"})
class DirectApiLiveHttpTest {
  private static final String TEST_KEY =
      "test-only-direct-backend-auth-hmac-secret-at-least-32-bytes";
  private static final SecretKey KEY =
      Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8));
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();

  @Value("${local.server.port}")
  private int port;
  @MockitoBean
  private UserService service;

  private String bearer(String role, List<String> scopes, Object uid, Instant expiry) {
    return "Bearer " + Jwts.builder()
      .issuer(DirectApiJwtFilter.DEFAULT_ISSUER)
      .audience().add(DirectApiJwtFilter.DEFAULT_AUDIENCE).and()
      .subject("http-check@example.invalid").claim("uid",uid)
      .claim("role",role).claim("scopes",scopes)
      .issuedAt(Date.from(Instant.now().minusSeconds(5)))
      .expiration(Date.from(expiry)).signWith(KEY).compact();
  }
  private HttpRequest.Builder to(String path) {
    assertTrue(port > 0);
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
      .timeout(Duration.ofSeconds(8));
  }
  private int code(HttpRequest req) throws Exception {
    return http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode();
  }

  @Test
  void realHttpAnonymousAndSpoofedHeadersCannotReadUsers() throws Exception {
    assertEquals(401,code(to("/api/users").GET().build()));
    assertEquals(401,code(to("/api/users").header("X-Aetheris-User-Role","ADMIN")
      .header("X-Aetheris-Scopes","users:read").GET().build()));
  }

  @Test
  void realHttpSignedRoleAndScopeAreBothRequired() throws Exception {
    assertEquals(401,code(to("/api/users")
      .header("Authorization","Bearer fake.unsigned.token").GET().build()));
    assertEquals(403,code(to("/api/users").header("Authorization",
      bearer("DEVELOPER",List.of("users:read"),42L,Instant.now().plusSeconds(120))).GET().build()));
    assertEquals(403,code(to("/api/users").header("Authorization",
      bearer("ADMIN",List.of("services:read"),42L,Instant.now().plusSeconds(120))).GET().build()));
    assertEquals(200,code(to("/api/users").header("Authorization",
      bearer("ADMIN",List.of("users:read"),42L,Instant.now().plusSeconds(120))).GET().build()));
  }

  @Test
  void realHttpWriteRequiresSignedAdminWriteScope() throws Exception {
    assertEquals(401,code(to("/api/users/7").DELETE().build()));
    assertEquals(403,code(to("/api/users/7").header("Authorization",
      bearer("ADMIN",List.of("users:read"),42L,Instant.now().plusSeconds(120))).DELETE().build()));
    assertEquals(403,code(to("/api/users/7").header("Authorization",
      bearer("API_CONSUMER",List.of("users:write"),42L,Instant.now().plusSeconds(120))).DELETE().build()));
    assertEquals(204,code(to("/api/users/7").header("Authorization",
      bearer("ADMIN",List.of("users:write"),42L,Instant.now().plusSeconds(120))).DELETE().build()));
  }

  @Test
  void realHttpRejectsExpiredOrFractionalUid() throws Exception {
    assertEquals(401,code(to("/api/users").header("Authorization",
      bearer("ADMIN",List.of("users:read"),42L,Instant.now().minusSeconds(10))).GET().build()));
    assertEquals(401,code(to("/api/users").header("Authorization",
      bearer("ADMIN",List.of("users:read"),42.5d,Instant.now().plusSeconds(120))).GET().build()));
  }
}
