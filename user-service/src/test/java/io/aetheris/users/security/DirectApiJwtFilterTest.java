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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
            .issuer(DirectApiJwtFilter.DEFAULT_ISSUER)
            .audience().add(DirectApiJwtFilter.DEFAULT_AUDIENCE).and()
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

    private String tokenWithUid(Object uid) {
        return Jwts.builder()
            .issuer(DirectApiJwtFilter.DEFAULT_ISSUER)
            .audience().add(DirectApiJwtFilter.DEFAULT_AUDIENCE).and()
            .subject("authenticated@example.invalid")
            .claim("uid", uid)
            .claim("role", "ADMIN")
            .claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(Instant.now().minusSeconds(5)))
            .expiration(Date.from(Instant.now().plusSeconds(120)))
            .signWith(key)
            .compact();
    }

    @Test
    void rejectsFractionalOrNonpositiveUidWithoutTruncatingIdentity() throws Exception {
        for (Object uid : List.of(42.5d, 0, -1L)) {
            mvc.perform(get("/api/users").header("Authorization",
                "Bearer " + tokenWithUid(uid)))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/users").header("Authorization",
            "Bearer " + tokenWithUid(42L)))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsDuplicatedAuthorizationHeadersEvenWhenBothTokensAreValid() throws Exception {
        String bearer = "Bearer " + tokenWithUid(42L);
        mvc.perform(get("/api/users")
                .header("Authorization", bearer, bearer))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void directUserDeletionNeedsSignedAdministrativeWriteScope() throws Exception {
        mvc.perform(delete("/api/users/7"))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/users/7")
                .header("X-Aetheris-User-Role", "ADMIN"))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/users/7")
                .header("Authorization", "Bearer " +
                    token("ADMIN", List.of("users:read"), Instant.now().plusSeconds(120))))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/7")
                .header("Authorization", "Bearer " +
                    token("API_CONSUMER", List.of("users:write"), Instant.now().plusSeconds(120))))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/7")
                .header("Authorization", "Bearer " +
                    token("ADMIN", List.of("users:write"), Instant.now().plusSeconds(120))))
            .andExpect(status().isNoContent());
    }

    @Test
    void rejectsShortSecretAtConstruction() {
        assertThrows(IllegalArgumentException.class,()->new DirectApiJwtFilter("not-long-enough"));
    }

    private String tokenForBoundary(String issuer, String audience) {
        var builder = Jwts.builder()
            .subject("auth-boundary@example.invalid")
            .claim("uid", 42L)
            .claim("role", "ADMIN")
            .claim("scopes", List.of("users:read"))
            .issuedAt(Date.from(Instant.now().minusSeconds(5)))
            .expiration(Date.from(Instant.now().plusSeconds(120)));
        if (issuer != null) builder.issuer(issuer);
        if (audience != null) builder.audience().add(audience).and();
        return builder.signWith(key).compact();
    }

    @Test
    void rejectsAuthenticallySignedWrongOrAbsentIssuerAudienceBeforeBackend() throws Exception {
        for (String token : List.of(
                tokenForBoundary("unrelated-issuer", DirectApiJwtFilter.DEFAULT_AUDIENCE),
                tokenForBoundary(DirectApiJwtFilter.DEFAULT_ISSUER, "unrelated-target"),
                tokenForBoundary(null, DirectApiJwtFilter.DEFAULT_AUDIENCE),
                tokenForBoundary(DirectApiJwtFilter.DEFAULT_ISSUER, null))) {
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/users").header("Authorization", "Bearer " +
            tokenForBoundary(DirectApiJwtFilter.DEFAULT_ISSUER, DirectApiJwtFilter.DEFAULT_AUDIENCE)))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsEmptyExpectedIssuerAudienceConfiguration() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectApiJwtFilter(TEST_ONLY_SECRET, "", DirectApiJwtFilter.DEFAULT_AUDIENCE));
        assertThrows(IllegalArgumentException.class, () ->
            new DirectApiJwtFilter(TEST_ONLY_SECRET, DirectApiJwtFilter.DEFAULT_ISSUER, ""));
    }
}
