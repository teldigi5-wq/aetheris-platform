package io.aetheris.users.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real filter invoked by MockMvc; no network, JWT secret in test code only. */
class DirectApiJwtFilterTest {
    private static final String TEST_ONLY_SECRET =
        "test-only-direct-backend-auth-hmac-secret-at-least-32-bytes";
    private final SecretKey key = Keys.hmacShaKeyFor(TEST_ONLY_SECRET.getBytes(StandardCharsets.UTF_8));
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(org.mockito.Mockito.mock(io.aetheris.users.UserService.class)))
             .addFilters(new DirectApiJwtFilter(TEST_ONLY_SECRET))
             .build();
    }

    private String token(String role, List<String> scopes, Instant expiry) {
        return Jwts.builder()
            .subject("authenticated@example.invalid")
            .claim("uid", 42L)
            .claim("role", role)
            .claim("scopes", scopes)
            .issuedAt(Date.from(Instant.now().minusSeconds(5)))
            .expiration(Date.from(expiry))
            .signWith(key)
            .compact();
    }

    @Test
    void rejectsMissingBearerAndFakeGatewayHeaders() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").header("X-Aetheris-User-Role","ADMIN")
            .header("X-Aetheris-Scopes","users:read"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedAndExpiredTokens() throws Exception {
        mvc.perform(get("/api/users").header("Authorization","Bearer fake.unsigned.token"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").header("Authorization","Bearer " +
            token("ADMIN",List.of("users:read"), Instant.now().minusSeconds(30))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesValidJwtWithoutAdministrativeRoleOrScope() throws Exception {
        mvc.perform(get("/api/users").header("Authorization","Bearer "+
            token("API_CONSUMER",List.of("users:read"),Instant.now().plusSeconds(120))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization","Bearer "+
            token("ADMIN",List.of("orchestrator:read"),Instant.now().plusSeconds(120))))
            .andExpect(status().isForbidden());
    }

    @Test
    void admitsSignedAdminWithAppropriateScope() throws Exception {
        mvc.perform(get("/api/users").header("Authorization","Bearer "+
            token("ADMIN",List.of("users:read"),Instant.now().plusSeconds(120))))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsShortSecretAtConstruction() {
        assertThrows(IllegalArgumentException.class,()->new DirectApiJwtFilter("not-long-enough"));
    }
}
