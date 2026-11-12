package io.aetheris.audit.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.aetheris.audit.AuditController;
import io.aetheris.audit.AuditEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
@Import(ServiceJwtAuthorizationFilter.class)
@TestPropertySource(properties = {
        "aetheris.jwt.secret=synthetic-test-only-strong-signing-key-0123456789",
        "aetheris.jwt.issuer=aetheris-identity",
        "aetheris.jwt.audience=aetheris-api"
})
class AuditWebSliceAuthorizationTest {
    private static final String SECRET = "synthetic-test-only-strong-signing-key-0123456789";
    @Autowired MockMvc mvc;
    @MockitoBean AuditEventStore service;

    private static String token(String role) {
        return Jwts.builder()
                .issuer("aetheris-identity")
                .audience().add("aetheris-api").and()
                .subject("fake-identity@example.test")
                .claim("uid", 7L)
                .claim("role", role)
                .claim("scopes", List.of("services:read"))
                .issuedAt(Date.from(Instant.now().minusSeconds(30)))
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void liveWebSliceRejectsDirectAnonymousApi() throws Exception {
        mvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void liveWebSliceDeniesOrdinarySignedAccount() throws Exception {
        mvc.perform(get("/api/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("API_CONSUMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void liveWebSliceAllowsSignedAdministrator() throws Exception {
        when(service.recent()).thenReturn(List.of());
        mvc.perform(get("/api/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN")))
                .andExpect(status().isOk());
        verify(service).recent();
    }
}
