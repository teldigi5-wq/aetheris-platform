package io.aetheris.users.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks Identity-minted test-only RSA JWTs with the real running servlet filter. */
class IdentityRs256B59VerifierIT {
    private static final Path ROOT=Path.of("..","identity-service","target","b59-rsa-contract").toAbsolutePath().normalize();
    private static final String ISSUER="aetheris-identity",AUD="aetheris-api",KID="b59-ephemeral-a";

    private static String read(String name) throws Exception {
        Path file=ROOT.resolve(name);
        assertTrue(file.startsWith(ROOT) && Files.isRegularFile(file) && !Files.isSymbolicLink(file));
        assertTrue(Files.size(file) <= 8192);
        return Files.readString(file,StandardCharsets.US_ASCII).trim();
    }

    @Test
    void acceptsOnlyAuthenticIdentityIssuedRsaAdminAndNeverHmacFallback() throws Exception {
        assertEquals("B59_EPHEMERAL_IDENTITY_RSA_V1",read("test-only.marker"));
        var filter=new DirectApiJwtFilter("",ISSUER,AUD,"rs256",read("public-x509.b64"),KID);
        var mvc=MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(org.mockito.Mockito.mock(io.aetheris.users.UserService.class))).addFilters(filter).build();
        mvc.perform(get("/api/users").header("Authorization","Bearer "+read("admin.jwt")))
            .andExpect(status().isOk());
        for (String attack: new String[]{"unknown-kid","wrong-issuer","wrong-audience",
                                        "wrong-signature","expired","legacy-hmac","tampered"}) {
            mvc.perform(get("/api/users").header("Authorization","Bearer "+read(attack+".jwt")))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingWeakOrMixedRsaVerifierConfigRefusesStartup() throws Exception {
        String b64=read("public-x509.b64");
        assertThrows(IllegalArgumentException.class, ()->
            new DirectApiJwtFilter("existing-hmac-secret-32-bytes-at-least",ISSUER,AUD,"rs256",b64,KID));
        assertThrows(IllegalArgumentException.class, ()->
            new DirectApiJwtFilter("",ISSUER,AUD,"rs256",b64,""));
        assertThrows(IllegalArgumentException.class, ()->
            new DirectApiJwtFilter("",ISSUER,AUD,"rs256","invalid-base64",KID));
        assertThrows(IllegalArgumentException.class, ()->
            new DirectApiJwtFilter("",ISSUER,AUD,"unsupported",b64,KID));
        assertThrows(IllegalArgumentException.class, ()->
            new DirectApiJwtFilter("",ISSUER,AUD,"hmac",b64,KID));
    }
}
