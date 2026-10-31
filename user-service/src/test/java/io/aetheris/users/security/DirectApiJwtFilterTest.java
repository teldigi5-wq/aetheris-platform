package io.aetheris.users.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual direct servlet filter, synthetic signed HMAC tokens. No real user
 * or key material, no deployment/migration, no production resource verdict.
 */
class DirectApiJwtFilterTest {
    private static final String SECRET = "CI_SYNTHETIC_SHARED_JWT_SECRET_64_BYTES_FOR_TESTS_ONLY_20261027";
    private static final String WRONG = "OTHER_SYNTHETIC_KEY_FROM_UNTRUSTED_CALLER_20261027_LONG_ENOUGH";

    private String signed(String secret, Object uid, String role, String scope,
                          Instant issued, Instant expires) {
        return Jwts.builder()
            .subject("synthetic@example.invalid")
            .claim("uid",uid).claim("role",role).claim("scopes",List.of(scope))
            .issuedAt(Date.from(issued)).expiration(Date.from(expires))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String valid(String role, String scope, long uid) {
        Instant now=Instant.now();
        return signed(SECRET,uid,role,scope,now.minusSeconds(5),now.plusSeconds(300));
    }

    private static String bearer(String token) { return "Bearer " + token; }

    @Test
    void noKeyNoBearerAndInvalidSignatureCannotCallController() throws Exception {
        io.aetheris.users.UserService service = org.mockito.Mockito.mock(io.aetheris.users.UserService.class);
        MockMvc unavailable = MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(service))
            .addFilters(new DirectApiJwtFilter("")).build();
        unavailable.perform(get("/api/users"))
            .andExpect(status().isServiceUnavailable());
        MockMvc api = MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(service))
            .addFilters(new DirectApiJwtFilter(SECRET)).build();
        api.perform(get("/api/users").header("X-Aetheris-User-Role","ADMIN")
            .header("X-Aetheris-Scopes","users:read")
            .header("X-Aetheris-User-Id","42"))
            .andExpect(status().isUnauthorized());
        Instant now=Instant.now();
        api.perform(get("/api/users").header("Authorization",bearer(
            signed(WRONG,42L,"ADMIN","users:read",now.minusSeconds(5),now.plusSeconds(100)))))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void signedExpiredOrMalformedIdentityCannotAccessBackend() throws Exception {
        io.aetheris.users.UserService service = org.mockito.Mockito.mock(io.aetheris.users.UserService.class);
        MockMvc api=MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(service))
            .addFilters(new DirectApiJwtFilter(SECRET)).build();
        Instant now=Instant.now();
        for (Object malformed : new Object[]{-1L,0L,3.14,"42"}) {
            api.perform(get("/api/users").header("Authorization",bearer(
                signed(SECRET,malformed,"ADMIN","users:read",
                    now.minusSeconds(15),now.plusSeconds(120)))))
                .andExpect(status().isUnauthorized());
        }
        api.perform(get("/api/users").header("Authorization",bearer(
            signed(SECRET,42L,"ADMIN","users:read",now.minusSeconds(7200),now.minusSeconds(60)))))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void signedRoleAndScopeDoNotBypassBackendPolicies() throws Exception {
        io.aetheris.users.UserService service = org.mockito.Mockito.mock(io.aetheris.users.UserService.class);
        MockMvc api=MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(service))
            .addFilters(new DirectApiJwtFilter(SECRET)).build();
        api.perform(get("/api/users").header("Authorization",bearer(
            valid("ADMIN","orchestrator:read",42))))
            .andExpect(status().isForbidden());
        api.perform(get("/api/users").header("Authorization",bearer(
            valid("UNRECOGNIZED","users:read",42))))
            .andExpect(status().isUnauthorized());
        api.perform(get("/api/users").header("Authorization",bearer(
            valid("API_CONSUMER","users:read",42))))
            .andExpect(status().isForbidden());
        api.perform(get("/api/users/43").header("Authorization",bearer(
            valid("API_CONSUMER","users:read",42))))
            .andExpect(status().isForbidden());
        // Even matching numeric IDs do NOT prove that identity and user
        // service rows belong to the same person or tenant.
        api.perform(get("/api/users/42").header("Authorization",bearer(
            valid("API_CONSUMER","users:read",42))))
            .andExpect(status().isForbidden());
        // Signed scopes outside a role's Identity-issued ceiling are not a
        // valid principal: reject with 401 before the route permission check.
        api.perform(delete("/api/users/42").header("Authorization",bearer(
            valid("API_CONSUMER","users:write",42))))
            .andExpect(status().isUnauthorized());
        // Even with an authentic consumer role + legitimate scope, this
        // unverified object ownership route remains forbidden.
        api.perform(delete("/api/users/42").header("Authorization",bearer(
            valid("API_CONSUMER","users:read",42))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void matrixDecoratedApiPathRequiresJwtBeforeControllerInvocation() throws Exception {
        io.aetheris.users.UserService service = org.mockito.Mockito.mock(io.aetheris.users.UserService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(service))
            .addFilters(new DirectApiJwtFilter(SECRET)).build();
        mvc.perform(get("/api/users;variant=1"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api;variant=1/users"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void authorizedSignedRequestsReachActualControllerOnlyWithinScope() throws Exception {
        io.aetheris.users.UserService service = org.mockito.Mockito.mock(io.aetheris.users.UserService.class);
        MockMvc api=MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(service))
            .addFilters(new DirectApiJwtFilter(SECRET)).build();
        
        when(service.findAll()).thenReturn(List.of());
        api.perform(get("/api/users").header("Authorization",bearer(
            valid("DEVELOPER","users:read",42))))
            .andExpect(status().isOk());
        api.perform(get("/api/users/42").header("Authorization",bearer(
            valid("DEVELOPER","users:read",42))))
            .andExpect(status().isOk());
        verify(service,times(1)).findAll();
        verify(service,times(1)).findById(42L);
        
    }
}
