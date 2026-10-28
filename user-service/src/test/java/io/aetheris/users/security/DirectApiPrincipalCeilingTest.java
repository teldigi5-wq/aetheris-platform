package io.aetheris.users.security;

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

/** Tests actual backend servlet security against signed principal escalation. */
class DirectApiPrincipalCeilingTest {
    private static final String SECRET =
        "CI_PRINCIPAL_ROLE_CEILING_SYNTHETIC_HMAC_NOT_FOR_PRODUCTION_20261028";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-user-api";

    private String signed(String subject, String role, List<String> scopes, KeyPair key) {
        Instant now = Instant.now();
        var jwt = Jwts.builder().subject(subject)
            .claim("uid", 42L).claim("role", role).claim("scopes", scopes)
            .issuer(ISSUER).claim("aud", List.of(AUDIENCE))
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(180)));
        return key == null
            ? jwt.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact()
            : jwt.signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private MockMvc backend(io.aetheris.users.UserService data, String pem) {
        return MockMvcBuilders.standaloneSetup(new io.aetheris.users.UserController(data))
            .addFilters(new DirectApiJwtFilter(SECRET, ISSUER, AUDIENCE, pem,
                Clock.systemUTC())).build();
    }

    private KeyPair syntheticRsa() throws Exception {
        var g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    @Test
    void signedHmacRoleScopeEscalationsAndUnnormalizedSubjectNeverReachController()
        throws Exception {
        io.aetheris.users.UserService data = mock(io.aetheris.users.UserService.class);
        MockMvc api = backend(data, "");
        for (String jwt : List.of(
            signed("consumer@example.invalid", "API_CONSUMER", List.of("users:write"), null),
            signed("developer@example.invalid", "DEVELOPER", List.of("identity:write"), null),
            signed("admin@example.invalid", "ADMIN", List.of("root:all"), null),
            signed(" admin@example.invalid ", "ADMIN", List.of("users:read"), null),
            signed("admin@example.invalid", "ADMIN", List.of("users:read", "unknown:privilege"), null)
        )) {
            api.perform(get("/api/users").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(data);
    }

    @Test
    void signedRs256RoleScopeEscalationsAreRejectedButLegitimatePrincipalStillWorks()
        throws Exception {
        KeyPair signer = syntheticRsa();
        io.aetheris.users.UserService data = mock(io.aetheris.users.UserService.class);
        when(data.findAll()).thenReturn(List.of());
        MockMvc api = backend(data, pem(signer));
        for (String jwt : List.of(
            signed("consumer@example.invalid", "API_CONSUMER", List.of("users:write"), signer),
            signed("developer@example.invalid", "DEVELOPER", List.of("identity:write"), signer),
            signed("admin@example.invalid", "ADMIN", List.of("root:all"), signer),
            signed(" admin@example.invalid ", "ADMIN", List.of("users:read"), signer)
        )) {
            api.perform(get("/api/users").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(data);
        api.perform(get("/api/users").header("Authorization", "Bearer " +
            signed("admin@example.invalid", "ADMIN", List.of("users:read"), signer)))
            .andExpect(status().isOk());
        verify(data, times(1)).findAll();
    }
}
