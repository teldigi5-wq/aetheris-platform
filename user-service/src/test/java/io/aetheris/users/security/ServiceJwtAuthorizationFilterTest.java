package io.aetheris.users.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import io.aetheris.users.UserController;
import io.aetheris.users.UserService;;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServiceJwtAuthorizationFilterTest {
    private static final String SECRET = "test-only-strong-non-production-secret-123456";
    private UserService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(UserService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .addFilters(new ServiceJwtAuthorizationFilter(SECRET))
                .build();
    }

    private String token(String role, List<String> scopes, Instant expiration,
                         String issuer, String audience, long id, String signingSecret) {
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("account@example.test")
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(expiration))
                .claim("uid", id)
                .claim("role", role)
                .claim("scopes", scopes)
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)),
                          Jwts.SIG.HS256)
                .compact();
    }

    private String bearer(String role, List<String> scopes) {
        return "Bearer " + token(role, scopes, Instant.now().plusSeconds(300),
                ServiceJwtAuthorizationFilter.DEFAULT_ISSUER,
                ServiceJwtAuthorizationFilter.DEFAULT_AUDIENCE, 42L, SECRET);
    }

    @Test
    void directAnonymousRequestRejectedBeforeController() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(service);
    }

    @Test
    void directRequestWithForgedInternalAdminHeaderAndNoBearerRejected() throws Exception {
        mvc.perform(get("/api/users")
                        .header("X-Aetheris-User-Role", "ADMIN")
                        .header("X-Aetheris-User-Id", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void consumerWithSignedGlobalReadScopeStillForbidden() throws Exception {
        mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("API_CONSUMER", List.of("users:read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void developerWithSignedGlobalReadScopeStillForbidden() throws Exception {
        mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("DEVELOPER", List.of("users:read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void administratorWithoutRequiredScopeForbidden() throws Exception {
        mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("orchestrator:read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void wrongIssuerAudienceAndExpiredTokensFailAuthentication() throws Exception {
        for (String bad : List.of(
                token("ADMIN", List.of("users:read"), Instant.now().plusSeconds(120),
                        "imposter", ServiceJwtAuthorizationFilter.DEFAULT_AUDIENCE, 42L, SECRET),
                token("ADMIN", List.of("users:read"), Instant.now().plusSeconds(120),
                        ServiceJwtAuthorizationFilter.DEFAULT_ISSUER, "wrong-app", 42L, SECRET),
                token("ADMIN", List.of("users:read"), Instant.now().minusSeconds(15),
                        ServiceJwtAuthorizationFilter.DEFAULT_ISSUER,
                        ServiceJwtAuthorizationFilter.DEFAULT_AUDIENCE, 42L, SECRET))) {
            mvc.perform(get("/api/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void malformedAndDuplicateBearerRejected() throws Exception {
        mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("users:read")),
                                bearer("ADMIN", List.of("users:read"))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void validAdministratorCanReachApprovedReadEndpoint() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("ADMIN", List.of("users:read")))
                        .header("X-Aetheris-User-Role", "API_CONSUMER"))
                .andExpect(status().isOk());
        verify(service).findAll();
    }

    @Test
    void administratorNeedsDistinctWriteScopeForDelete() throws Exception {
        mvc.perform(delete("/api/users/7")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("users:read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(delete("/api/users/7")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("users:write"))))
                .andExpect(status().isNoContent());
        verify(service).delete(7L);
    }

    @Test
    void consumerCannotFetchAnotherUserById() throws Exception {
        mvc.perform(get("/api/users/999")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("API_CONSUMER", List.of("users:read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unsafeKnownLocalSigningSecretIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceJwtAuthorizationFilter("change-me-local-development-secret-32chars"));
    }
}
