package io.aetheris.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tests production Identity JwtService issuance; no real key or tenant data. */
class JwtIssuerAudienceIssuanceTest {
    private static final String SECRET =
        "SYNTHETIC_IDENTITY_JWT_SIGNING_KEY_NOT_FOR_PRODUCTION_20261027_LONG";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String USER_AUDIENCE = "aetheris-user-api";

    private static IdentityAccount account() {
        IdentityAccount a = new IdentityAccount(
            "Synthetic", "issuer-audience@example.invalid",
            "not-a-real-password-hash", Role.API_CONSUMER);
        ReflectionTestUtils.setField(a, "id", 42L);
        return a;
    }

    private static Claims verified(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(token).getPayload();
    }

    @Test
    void targetedIssuerProducesRealSingleAudienceSignedJwt() {
        JwtService service = new JwtService(SECRET, 3600, ISSUER, USER_AUDIENCE);
        Claims claims = verified(service.issue(account()));
        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(Set.of(USER_AUDIENCE), claims.getAudience());
        assertEquals(42L, ((Number) claims.get("uid")).longValue());
        assertEquals("API_CONSUMER", claims.get("role", String.class));
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
        assertNull(claims.get("tenant_id"));
    }

    @Test
    void legacyDefaultsPreserveUnchangedClaimContract() {
        JwtService service = new JwtService(SECRET, 7200);
        Claims claims = verified(service.issue(account()));
        assertNull(claims.getIssuer());
        assertTrue(claims.getAudience() == null || claims.getAudience().isEmpty());
        assertEquals(42L, ((Number) claims.get("uid")).longValue());
        assertEquals(7200L, service.expirationSeconds());
    }

    @Test
    void partialBlankOrMalformedTargetSettingsFailClosedOnConstruction() {
        for (String[] values : new String[][] {
            {ISSUER, ""}, {"", USER_AUDIENCE},
            {" ", USER_AUDIENCE}, {ISSUER, " "},
            {" " + ISSUER, USER_AUDIENCE},
            {ISSUER, USER_AUDIENCE + " "}
        }) {
            assertThrows(IllegalArgumentException.class,
                () -> new JwtService(SECRET, 3600, values[0], values[1]));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 0, ISSUER, USER_AUDIENCE));
    }
}
