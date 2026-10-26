package io.aetheris.audit.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdentityRs256B60RotationIT {
    private static final Path ROOT=Path.of("..","identity-service","target","b60-rotation").toAbsolutePath().normalize();
    private static final String ISSUER="aetheris-identity", AUDIENCE="aetheris-api";
    private static String read(String name) throws Exception {
        Path file=ROOT.resolve(name);
        assertTrue(file.startsWith(ROOT) && Files.isRegularFile(file) && !Files.isSymbolicLink(file));
        assertTrue(Files.size(file)<8192);
        return Files.readString(file,StandardCharsets.US_ASCII).trim();
    }
    private static DirectApiJwtFilter filter(String previousKey, String previousKid) throws Exception {
        return new DirectApiJwtFilter("", ISSUER, AUDIENCE,"rs256",
            read("current-public-x509.b64"),"b60-current",previousKey,previousKid,
            previousKid.isBlank() ? "" : Long.toString(java.time.Instant.now().plusSeconds(600).getEpochSecond()));
    }
    @Test
    void overlapAdmitsExactlyBothVerifiedSigningKeysAndRejectsKidRelabeling() throws Exception {
        assertEquals("B60_EPHEMERAL_PUBLIC_ROLLOVER_V1",read("test-only.marker"));
        var mvc=MockMvcBuilders.standaloneSetup(new io.aetheris.audit.AuditController(org.mockito.Mockito.mock(io.aetheris.audit.AuditEventStore.class)))
            .addFilters(filter(read("previous-public-x509.b64"),"b60-previous")).build();
        for(String allowed:new String[]{"current","previous"}){
            mvc.perform(get("/api/events").header("Authorization","Bearer "+read(allowed+".jwt")))
                .andExpect(status().isOk());
        }
        for(String denied:new String[]{"relabel-previous","unknown-kid","untrusted-key",
                                      "foreign-issuer","workload-only"}){
            mvc.perform(get("/api/events").header("Authorization","Bearer "+read(denied+".jwt")))
                .andExpect(status().isUnauthorized());
        }
    }
    @Test
    void deletingPreviousPublicKeyImmediatelyDeniesPreviouslyValidOldTokens() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new io.aetheris.audit.AuditController(org.mockito.Mockito.mock(io.aetheris.audit.AuditEventStore.class)))
            .addFilters(filter("","")).build();
        mvc.perform(get("/api/events").header("Authorization","Bearer "+read("current.jwt")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/events").header("Authorization","Bearer "+read("previous.jwt")))
            .andExpect(status().isUnauthorized());
    }
    @Test
    void partialDuplicateInvalidOrHmacMixedTrustStoreFailsBeforeFirstRequest() throws Exception {
        String current=read("current-public-x509.b64"),previous=read("previous-public-x509.b64");
        assertThrows(IllegalArgumentException.class,()->filter(previous,""));
        assertThrows(IllegalArgumentException.class,()->filter("","b60-previous"));
        assertThrows(IllegalArgumentException.class,()->filter(previous,"b60-current"));
        assertThrows(IllegalArgumentException.class,()->filter(current,"b60-previous"));
        assertThrows(IllegalArgumentException.class,()->filter("invalid-b64","b60-previous"));
        assertThrows(IllegalArgumentException.class,()->new DirectApiJwtFilter(
            "test-only-HMAC-do-not-mix-strong-secret-64-bytes",ISSUER,AUDIENCE,"rs256",
            current,"b60-current",previous,"b60-previous"));
    }
}
