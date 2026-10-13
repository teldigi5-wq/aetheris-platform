package io.aetheris.identity;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceIssuerAudienceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issuesPlatformScopedJwtWithActualPersistedNumericId() {
        IdentityAccount account = new IdentityAccount("Sample", "owner@example.test",
                "synthetic-hash", Role.API_CONSUMER);
        ReflectionTestUtils.setField(account, "id", 42L);
        JwtService service = new JwtService(SECRET, 3600);
        String jwt = service.issue(account);
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(JwtService.DEFAULT_ISSUER)
                .requireAudience(JwtService.DEFAULT_AUDIENCE)
                .build().parseSignedClaims(jwt).getPayload();
        assertEquals("owner@example.test", claims.getSubject());
        assertEquals(42L, ((Number) claims.get("uid")).longValue());
        assertEquals("API_CONSUMER", claims.get("role", String.class));
        assertTrue(((Collection<?>) claims.get("scopes")).contains("orchestrator:read"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void cannotIssueForUnpersistedIdentity() {
        IdentityAccount unsaved = new IdentityAccount("Sample", "owner@example.test",
                "synthetic-hash", Role.API_CONSUMER);
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, 3600).issue(unsaved));
    }

    @Test
    void issuerAudienceAndExpiryConfigurationFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, 3600, "", JwtService.DEFAULT_AUDIENCE));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, 3600, JwtService.DEFAULT_ISSUER, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, 90000));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("short", 3600));
    }
}
