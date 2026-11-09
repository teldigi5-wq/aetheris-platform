package io.aetheris.users.security;

import io.aetheris.users.UserController;
import io.aetheris.users.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TenantServiceJwtAuthorizationTest {
    private static final String SECRET = "synthetic-user-tenant-service-secret-0123456789";
    private static final UUID TENANT = UUID.fromString("e398c9b9-23ea-473a-8240-8c42a92f7a34");
    private UserService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(UserService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .addFilters(new ServiceJwtAuthorizationFilter(SECRET)).build();
    }

    private String jwt(String role, List<String> scopes, String tid) {
        var builder = Jwts.builder().issuer(ServiceJwtAuthorizationFilter.DEFAULT_ISSUER)
                .audience().add(ServiceJwtAuthorizationFilter.DEFAULT_AUDIENCE).and()
                .subject("synthetic-account@example.test")
                .claim("uid", 27L).claim("role", role).claim("scopes", scopes)
                .issuedAt(Date.from(Instant.now().minusSeconds(25)))
                .expiration(Date.from(Instant.now().plusSeconds(200)));
        if (tid != null) builder.claim("tid", tid);
        return builder.signWith(Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    @Test
    void signedTenantProducesOnlyServerSideRequestAttribute() throws Exception {
        when(service.findAllForTenant(TENANT)).thenReturn(List.of());
        mvc.perform(get("/api/users/tenant")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt("API_CONSUMER",
                        List.of("tenant:users:read"), TENANT.toString()))
                .header("X-Aetheris-Tenant-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        verify(service).findAllForTenant(TENANT);
    }

    @Test
    void scopedObjectLookupForwardsOnlyVerifiedTenantUuid() throws Exception {
        mvc.perform(get("/api/users/tenant/43")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt("API_CONSUMER",
                        List.of("tenant:users:read"), TENANT.toString())))
                .andExpect(status().isOk());
        verify(service).findByIdForTenant(43L, TENANT);
        verify(service, never()).findById(43L);
    }

    @Test
    void signedTenantDeleteRequiresWriteScopeAndTenantPredicate() throws Exception {
        mvc.perform(delete("/api/users/tenant/7")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt("API_CONSUMER",
                        List.of("tenant:users:read"), TENANT.toString())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(delete("/api/users/tenant/7")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt("API_CONSUMER",
                        List.of("tenant:users:write"), TENANT.toString())))
                .andExpect(status().isNoContent());
        verify(service).deleteForTenant(7L, TENANT);
        verify(service, never()).delete(7L);
    }

    @Test
    void globalDataStillRequiresAdministrator() throws Exception {
        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " +
                jwt("API_CONSUMER", List.of("users:read", "tenant:users:read"),
                        TENANT.toString()))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void maliciousTenantHeaderCannotReplaceMissingSignedClaim() throws Exception {
        mvc.perform(get("/api/users/tenant")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                        jwt("API_CONSUMER", List.of("tenant:users:read"), null))
                .header("X-Aetheris-Tenant-Id", TENANT.toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void malformedOrUppercaseSignedUuidFailsClosed() throws Exception {
        for (String tid : List.of("not-uuid", TENANT.toString().toUpperCase())) {
            mvc.perform(get("/api/users/tenant")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                            jwt("API_CONSUMER", List.of("tenant:users:read"), tid)))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void administratorWithTenantClaimCannotEnterScopedConsumerPath() throws Exception {
        mvc.perform(get("/api/users/tenant")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " +
                        jwt("ADMIN", List.of("tenant:users:read"), TENANT.toString())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousAndHeaderOnlyTenantCannotReachController() throws Exception {
        mvc.perform(get("/api/users/tenant")
                .header("X-Aetheris-Tenant-Id", TENANT.toString()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
