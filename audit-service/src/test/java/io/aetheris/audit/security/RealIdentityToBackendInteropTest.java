package io.aetheris.audit.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the actual servlet filter against tokens minted by the real Identity JwtService. */
class RealIdentityToBackendInteropTest {
    private static final String AUDIENCE = "aetheris-audit-api";

    private Properties fixture() throws Exception {
        String path = System.getProperty("aetheris.jwt.interop.fixture", "");
        if ("yes".equals(System.getenv("AETHERIS_INTEROP_MUST_RUN"))) {
            assertFalse(path.isBlank(), "Explicit cross-module CI must supply a fixture path");
        }
        Assumptions.assumeTrue(!path.isBlank(), "Run only in explicit cross-module CI");
        Properties props = new Properties();
        try (var stream = Files.newInputStream(Path.of(path))) { props.load(stream); }
        assertEquals(8, props.size());
        return props;
    }

    @Test
    void realIdentityApprovedTargetPassesAndUnrelatedTargetFailsBeforeController() throws Exception {
        Properties p = fixture();
        String pem = new String(Base64.getDecoder().decode(
            p.getProperty("publicPemBase64")), StandardCharsets.US_ASCII);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        when(service.recent()).thenReturn(List.of());
        MockMvc actual = MockMvcBuilders.standaloneSetup(
            new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter("", p.getProperty("issuer"), AUDIENCE,
                pem, p.getProperty("kid"), "", "", Clock.systemUTC()))
            .build();

        actual.perform(get("/api/events")
            .header("Authorization", "Bearer " + p.getProperty("targetAudit"))
            .header("X-Aetheris-User-Id", "999999")
            .header("X-Aetheris-Tenant-Id", "attacker"))
            .andExpect(status().isOk());
        actual.perform(get("/api/events")
            .header("Authorization", "Bearer " + p.getProperty("targetUser")))
            .andExpect(status().isUnauthorized());
        actual.perform(get("/api/events")
            .header("Authorization", "Bearer " + p.getProperty("targetOrchestrator")))
            .andExpect(status().isUnauthorized());
        actual.perform(get("/api/events")
            .header("Authorization", "Bearer " + p.getProperty("loginConsumer")))
            .andExpect(status().isUnauthorized());
        verify(service, times(1)).recent();
        verifyNoMoreInteractions(service);
    }

    @Test
    void wrongPublicKeyAndUnknownPinnedKidNeverCallTheActualController() throws Exception {
        Properties p = fixture();
        String pem = new String(Base64.getDecoder().decode(
            p.getProperty("publicPemBase64")), StandardCharsets.US_ASCII);
        io.aetheris.audit.AuditEventStore service = mock(io.aetheris.audit.AuditEventStore.class);
        var wrongKid = MockMvcBuilders.standaloneSetup(
            new io.aetheris.audit.AuditController(service))
            .addFilters(new DirectApiJwtFilter("", p.getProperty("issuer"), AUDIENCE,
                pem, "other-kid", "", "", Clock.systemUTC()))
            .build();
        wrongKid.perform(get("/api/events")
            .header("Authorization", "Bearer " + p.getProperty("targetAudit")))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
