package io.aetheris.audit.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actual servlet filter with synthetic JWTs for opt-in issuer/audience policy.
 * This does not enable production token issuance, tenants, or deploy authority.
 */
class DirectApiIssuerAudiencePolicyTest {
    private static final String SECRET =
        "CI_ONLY_SIGNING_KEY_BACKEND_AUDIENCE_POLICY_20261027_64_BYTES_LONG";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-audit-api";
    private static final String ROUTE = "/api/events";

    private String signed(String issuer, List<String> audiences, String role, String scope) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject("synthetic@example.invalid")
            .claim("uid", 42L).claim("role", role).claim("scopes", List.of(scope))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(300)));
        if (issuer != null) builder.issuer(issuer);
        if (audiences != null) builder.claim("aud", audiences);
        return builder.signWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private MockMvc api(io.aetheris.audit.AuditEventStore service, String issuer, String audience) {
        return MockMvcBuilders.standaloneSetup(new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter(SECRET, issuer, audience, Clock.systemUTC()))
            .build();
    }

    @Test
    void validExactIssuerAndSoleAudiencePermitOnlyAlreadyPrivilegedRoute() throws Exception {
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        when(service.recent()).thenReturn(List.of());
        MockMvc mvc = api(service, ISSUER, AUDIENCE);
        mvc.perform(get(ROUTE).header("Authorization", "Bearer "
            + signed(ISSUER, List.of(AUDIENCE), "ADMIN", "services:read")))
            .andExpect(status().isOk());
        verify(service, times(1)).recent();
        mvc.perform(get(ROUTE).header("Authorization", "Bearer "
            + signed(ISSUER, List.of(AUDIENCE), "API_CONSUMER", "services:read")))
            .andExpect(status().isForbidden());
        verifyNoMoreInteractions(service);
    }

    @Test
    void missingWrongAndMultiAudienceTokensAreDeniedBeforeController() throws Exception {
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        MockMvc mvc = api(service, ISSUER, AUDIENCE);
        String[] invalid = {
            signed(null, List.of(AUDIENCE), "ADMIN", "services:read"),
            signed("https://different-issuer.invalid", List.of(AUDIENCE), "ADMIN", "services:read"),
            signed(ISSUER, null, "ADMIN", "services:read"),
            signed(ISSUER, List.of("another-service"), "ADMIN", "services:read"),
            signed(ISSUER, List.of(AUDIENCE, "other-service"), "ADMIN", "services:read")
        };
        for (String jwt : invalid) {
            mvc.perform(get(ROUTE)
                .header("Authorization", "Bearer " + jwt)
                .header("X-Aetheris-User-Role", "ADMIN")
                .header("X-Aetheris-Scopes", "services:read"))
                .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void incompleteOrMalformedOperatorPolicyFailsClosedWith503() throws Exception {
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        for (String[] pair : new String[][] {
            {ISSUER, ""}, {"", AUDIENCE}, {" "+ISSUER, AUDIENCE},
            {ISSUER, AUDIENCE+" "}, {" ", AUDIENCE}
        }) {
            api(service, pair[0], pair[1])
                .perform(get(ROUTE).header("Authorization", "Bearer "
                    + signed(ISSUER, List.of(AUDIENCE), "ADMIN", "services:read")))
                .andExpect(status().isServiceUnavailable());
        }
        verifyNoInteractions(service);
    }

    @Test
    void defaultLegacyModeDoesNotSilentlyGrantNewPermissions() throws Exception {
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        when(service.recent()).thenReturn(List.of());
        // Compatibility only: existing deployed Identity JWT lacks iss/aud.
        api(service, "", "")
            .perform(get(ROUTE).header("Authorization", "Bearer "
                + signed(null, null, "ADMIN", "services:read")))
            .andExpect(status().isOk());
        verify(service, times(1)).recent();
    }
}
