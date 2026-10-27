package io.aetheris.audit.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual servlet filter + controller; never accesses production identities. */
class DirectApiRs256VerificationTest {
    private static final String SECRET =
        "CI_SYNTHETIC_LEGACY_HMAC_FOR_RSA_DOWNGRADE_TEST_ONLY_20261027";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-audit-api";

    private KeyPair pair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private String jwt(KeyPair pair, String audience, String role) {
        Instant now = Instant.now();
        var builder = Jwts.builder().subject("admin@example.invalid")
            .claim("uid", 42L).claim("role", role)
            .claim("scopes", List.of("services:read"))
            .issuer(ISSUER).claim("aud", List.of(audience))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(180)));
        return pair == null
            ? builder.signWith(Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8))).compact()
            : builder.signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private MockMvc api(io.aetheris.audit.AuditEventStore service, String pem) {
        return MockMvcBuilders.standaloneSetup(
            new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter(SECRET, ISSUER, AUDIENCE, pem, Clock.systemUTC()))
            .build();
    }

    @Test
    void acceptsOnlySignedRs256ForApprovedIssuerAndAudience() throws Exception {
        KeyPair signer = pair(2048);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        when(service.recent()).thenReturn(List.of());
        api(service, pem(signer)).perform(get("/api/events")
            .header("Authorization", "Bearer " + jwt(signer, AUDIENCE, "ADMIN")))
            .andExpect(status().isOk());
        verify(service, times(1)).recent();
    }

    @Test
    void rejectsHmacWrongSignerAndWrongAudienceWithoutController() throws Exception {
        KeyPair signer = pair(2048);
        KeyPair attacker = pair(2048);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        MockMvc api = api(service, pem(signer));
        api.perform(get("/api/events").header("Authorization",
            "Bearer " + jwt(null, AUDIENCE, "ADMIN")))
            .andExpect(status().isUnauthorized());
        api.perform(get("/api/events").header("Authorization",
            "Bearer " + jwt(attacker, AUDIENCE, "ADMIN")))
            .andExpect(status().isUnauthorized());
        api.perform(get("/api/events").header("Authorization",
            "Bearer " + jwt(signer, "other-audience", "ADMIN")))
            .andExpect(status().isUnauthorized());
        api.perform(get("/api/events").header("Authorization",
            "Bearer " + jwt(signer, AUDIENCE, "API_CONSUMER")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void invalidKeyAndIncompleteTargetPolicyFailClosed() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new DirectApiJwtFilter(SECRET, ISSUER, AUDIENCE, "invalid", Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class,
            () -> new DirectApiJwtFilter(SECRET, ISSUER, AUDIENCE,
                pem(pair(1024)), Clock.systemUTC()));
        KeyPair valid = pair(2048);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        MockMvc partial = MockMvcBuilders.standaloneSetup(
            new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter(SECRET, "", "",
                pem(valid), Clock.systemUTC())).build();
        partial.perform(get("/api/events").header("Authorization",
            "Bearer " + jwt(valid, AUDIENCE, "ADMIN")))
            .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(service);
    }
}
