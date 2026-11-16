package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production constructor guards must reject invalid access token lifetimes.
 * Negative/overlong RSA and HMAC fixtures have separate test-only signers.
 */
class JwtServiceLifetimeB62DTest {
    private static final String TEST_KEY =
        "b62d-test-only-jwt-signing-material-32bytes-or-longer";

    @Test
    void signerRefusesZeroNegativeAndOverlongAccessTokens() {
        for (long seconds : new long[] { -120L, -1L, 0L, 3601L, Long.MAX_VALUE }) {
            assertThrows(IllegalArgumentException.class,
                () -> new JwtService(TEST_KEY, seconds),
                "Expected fail-closed expiry configuration for " + seconds);
            // An invalid lifetime must fail even before attempting RSA decoding.
            assertThrows(IllegalArgumentException.class,
                () -> new JwtService("", seconds, JwtService.DEFAULT_ISSUER,
                    JwtService.DEFAULT_AUDIENCE, "rs256", "intentionally-invalid-key", "test-kid"));
        }
    }

    @Test
    void signerAcceptsOneSecondAndExactlyOneHour() {
        assertEquals(1L, new JwtService(TEST_KEY, 1L).expirationSeconds());
        assertEquals(3600L, new JwtService(TEST_KEY, 3600L).expirationSeconds());
    }

    @Test
    void realSignedMaxDurationIsExactlyOneHour() {
        JwtService issuer = new JwtService(TEST_KEY, 3600L);
        IdentityAccount account = new IdentityAccount("Synthetic B62D",
            "synthetic-b62d@example.invalid", "unused-test-only-hash", Role.ADMIN);
        ReflectionTestUtils.setField(account, "id", 42L);

        String jwt = issuer.issue(account);
        var signed = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8)))
            .requireIssuer(JwtService.DEFAULT_ISSUER)
            .requireAudience(JwtService.DEFAULT_AUDIENCE)
            .build().parseSignedClaims(jwt);
        var claims = signed.getPayload();
        assertEquals(3600L, Duration.between(
            claims.getIssuedAt().toInstant(),
            claims.getExpiration().toInstant()).getSeconds());
        assertEquals(42L, ((Number) claims.get("uid")).longValue());
    }
}
