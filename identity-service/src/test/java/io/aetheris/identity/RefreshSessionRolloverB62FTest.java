package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B62-F: controlled synthetic session lifecycle, not a production revocation
 * or database-concurrency certification. The repository is explicitly mocked.
 */
class RefreshSessionRolloverB62FTest {
    private static final String TEST_SIGNER =
        "b62f-synthetic-only-hmac-signing-material-at-least-32bytes";

    @Test
    void refreshConfigurationRejectsUnboundedSessionLifetimes() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        for (long ttl : new long[]{-1L, 0L, 604801L, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                () -> new RefreshTokenService(repository, ttl));
        }
        assertEquals(1L, new RefreshTokenService(repository, 1L).expirationSeconds());
        assertEquals(604800L, new RefreshTokenService(repository, 604800L).expirationSeconds());
        assertThrows(NullPointerException.class, () -> new RefreshTokenService(null, 300L));
    }

    @Test
    void logoutOnlyRevokesPresentedRefreshSessionWhileOtherSessionCanRotateAfterRestart() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        Map<String, RefreshToken> rows = new HashMap<>();
        when(repository.save(any(RefreshToken.class))).thenAnswer(call -> {
            RefreshToken token = call.getArgument(0);
            rows.put(token.getTokenHash(), token);
            return token;
        });
        when(repository.findByTokenHash(anyString())).thenAnswer(call ->
            Optional.ofNullable(rows.get(call.getArgument(0))));
        when(repository.revokeIfActive(anyString(), any(Instant.class))).thenAnswer(call -> {
            String tokenHash = call.getArgument(0);
            Instant current = call.getArgument(1);
            RefreshToken row = rows.get(tokenHash);
            if (row == null || row.isRevoked() || !row.getExpiresAt().isAfter(current)) {
                return 0;
            }
            row.revoke();
            return 1;
        });

        IdentityAccount account = new IdentityAccount("B62F Synthetic",
            "b62f-session@example.invalid", "unused-synthetic-hash", Role.API_CONSUMER);
        ReflectionTestUtils.setField(account, "id", 42L);
        RefreshTokenService sessions = new RefreshTokenService(repository, 604800L);
        var first = sessions.issue(account);
        var second = sessions.issue(account);
        assertNotEquals(first.token(), second.token());
        assertEquals(2, rows.size());
        assertTrue(rows.values().stream().allMatch(t -> t.getTokenHash().length() == 64));
        assertTrue(rows.values().stream().noneMatch(t ->
            t.getTokenHash().equals(first.token()) || t.getTokenHash().equals(second.token())));

        // A real, signed synthetic access JWT remains cryptographically valid
        // following refresh logout. This is a limitation, not instant revocation.
        var jwt = new JwtService(TEST_SIGNER, 300L).issue(account);
        var parser = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(TEST_SIGNER.getBytes(StandardCharsets.UTF_8)))
            .requireIssuer(JwtService.DEFAULT_ISSUER)
            .requireAudience(JwtService.DEFAULT_AUDIENCE).build();
        assertEquals(42L, ((Number) parser.parseSignedClaims(jwt)
            .getPayload().get("uid")).longValue());

        sessions.revoke(first.token());
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class,
            () -> sessions.rotate(first.token()));

        // A simulated service restart retains the persisted mock repository;
        // the independent second session is still valid, by current design.
        RefreshTokenService afterRestart = new RefreshTokenService(repository, 604800L);
        var rotated = afterRestart.rotate(second.token());
        assertEquals(account, rotated.account());
        assertNotEquals(second.token(), rotated.refreshToken().token());
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class,
            () -> afterRestart.rotate(second.token()));
        assertEquals(3, rows.size());

        // Logout revokes refresh tokens only; stateless JWT verifiers do not
        // query this DB. Never label this as emergency access-token revocation.
        assertEquals(42L, ((Number) parser.parseSignedClaims(jwt)
            .getPayload().get("uid")).longValue());
    }
}
