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
    void rsaOnlyFilterCannotSkipMatrixDecoratedApiRoutes() throws Exception {
        KeyPair signer = pair(2048);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        MockMvc mvc = api(service, pem(signer));
        mvc.perform(get("/api/events;variant=1"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api;variant=1/events"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events;variant=1")
            .header("Authorization", "Bearer " + jwt(signer, AUDIENCE, "ADMIN")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
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
    void realServletRejectsRetiredKeyAfterPinnedRotation() throws Exception {
        KeyPair older = pair(2048), newer = pair(2048);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        when(service.recent()).thenReturn(List.of());
        MockMvc during = MockMvcBuilders.standaloneSetup(
            new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter(SECRET, ISSUER, AUDIENCE,
                pem(older), "old", pem(newer), "new", Clock.systemUTC())).build();
        Instant now = Instant.now();
        String oldToken = Jwts.builder().header().keyId("old").and()
            .subject("admin@example.invalid").claim("uid", 42L)
            .claim("role", "ADMIN").claim("scopes", List.of("services:read"))
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(older.getPrivate(), Jwts.SIG.RS256).compact();
        String newToken = Jwts.builder().header().keyId("new").and()
            .subject("admin@example.invalid").claim("uid", 42L)
            .claim("role", "ADMIN").claim("scopes", List.of("services:read"))
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(newer.getPrivate(), Jwts.SIG.RS256).compact();
        during.perform(get("/api/events").header("Authorization", "Bearer " + oldToken))
            .andExpect(status().isOk());
        during.perform(get("/api/events").header("Authorization", "Bearer " + newToken))
            .andExpect(status().isOk());
        MockMvc afterRetirement = MockMvcBuilders.standaloneSetup(
            new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter(SECRET, ISSUER, AUDIENCE,
                pem(newer), "new", "", "", Clock.systemUTC())).build();
        afterRetirement.perform(get("/api/events").header("Authorization", "Bearer " + oldToken))
            .andExpect(status().isUnauthorized());
        afterRetirement.perform(get("/api/events").header("Authorization", "Bearer " + newToken))
            .andExpect(status().isOk());
        verify(service, times(3)).recent();
    }

    @Test
    void publicKeyOnlyServletAcceptsRsaWithoutAnyLegacyHmacKey() throws Exception {
        KeyPair signer = pair(2048);
        for (String absentHmac : new String[]{"", "short", null}) {
            io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
            when(service.recent()).thenReturn(List.of());
            MockMvc rsaOnly = MockMvcBuilders.standaloneSetup(
                new io.aetheris.audit.AuditController(service))
                .addFilters(new DirectApiJwtFilter(
                    absentHmac, ISSUER, AUDIENCE, pem(signer), Clock.systemUTC()))
                .build();
            rsaOnly.perform(get("/api/events").header("Authorization",
                "Bearer " + jwt(signer, AUDIENCE, "ADMIN")))
                .andExpect(status().isOk());
            rsaOnly.perform(get("/api/events").header("Authorization",
                "Bearer " + jwt(null, AUDIENCE, "ADMIN")))
                .andExpect(status().isUnauthorized());
            verify(service, times(1)).recent();
            MockMvc missingBoth = MockMvcBuilders.standaloneSetup(
                new io.aetheris.audit.AuditController(service))
                .addFilters(new DirectApiJwtFilter(
                    absentHmac, ISSUER, AUDIENCE, "", Clock.systemUTC()))
                .build();
            missingBoth.perform(get("/api/events").header("Authorization",
                "Bearer " + jwt(signer, AUDIENCE, "ADMIN")))
                .andExpect(status().isServiceUnavailable());
            verifyNoMoreInteractions(service);
        }
    }

    @Test
    void rs256SignedUnverifiedTenantClaimCannotGrantAuditVisibility() throws Exception {
        KeyPair signer = pair(2048);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        MockMvc mvc = api(service, pem(signer));
        Instant now = Instant.now();

        // A valid RSA signature does not turn a caller-asserted tenant into
        // authorization for a privileged audit-data endpoint.
        for (String tenant : List.of("11111111-1111-4111-8111-111111111111",
                                     "22222222-2222-4222-8222-222222222222")) {
            String asserted = Jwts.builder()
                .subject("synthetic-consumer@example.invalid")
                .claim("uid", 42L).claim("role", "API_CONSUMER")
                .claim("scopes", List.of("services:read"))
                .claim("tenant_id", tenant)
                .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(180)))
                .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
            mvc.perform(get("/api/events")
                .header("Authorization", "Bearer " + asserted)
                .header("X-Aetheris-Tenant-Id", tenant)
                .header("X-Aetheris-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        }
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
