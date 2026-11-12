package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** B57: issuer must be the real Identity signing code, never a fake verifier. */
class JwtIssuerAudienceB57Test {
    private static final String KEY = "only-a-synthetic-identity-issuer-secret-for-B57-tests";

    private IdentityAccount synthetic() {
        IdentityAccount user = new IdentityAccount("B57 Synthetic", "identity-b57@example.invalid",
                "not-a-real-hash", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    @Test
    void actualIssuerSignsBothRequiredClaimsWithExplicitConfiguration() {
        String token = new JwtService(KEY, 300, "aetheris-identity", "aetheris-api").issue(synthetic());
        var claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8)))
            .requireIssuer("aetheris-identity").requireAudience("aetheris-api")
            .build().parseSignedClaims(token).getPayload();
        assertEquals(42L, ((Number) claims.get("uid")).longValue());
        assertEquals("identity-b57@example.invalid", claims.getSubject());
    }

    @Test
    void mismatchedIssuerAndAudienceMustNotValidate() {
        String token = new JwtService(KEY, 300).issue(synthetic());
        var key = Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> Jwts.parser().verifyWith(key)
            .requireIssuer("unrelated-issuer").requireAudience("aetheris-api")
            .build().parseSignedClaims(token));
        assertThrows(Exception.class, () -> Jwts.parser().verifyWith(key)
            .requireIssuer("aetheris-identity").requireAudience("unrelated-target")
            .build().parseSignedClaims(token));
    }

    @Test
    void invalidIssuerAudienceOrShortSigningSecretAreRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(KEY,300,"","aetheris-api"));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(KEY,300,"aetheris-identity",""));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("short",300));
    }
}
