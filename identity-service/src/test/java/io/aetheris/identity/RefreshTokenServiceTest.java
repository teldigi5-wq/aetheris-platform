package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    @Test
    void rotationAtomicallyClaimsPresentedTokenAndIssuesReplacement() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);

        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken first = service.issue(account);
        ArgumentCaptor<RefreshToken> created = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(created.capture());
        RefreshToken persisted = created.getValue();

        when(repository.findByTokenHash(persisted.getTokenHash())).thenReturn(Optional.of(persisted));
        when(repository.revokeIfActive(eq(persisted.getTokenHash()), any(Instant.class))).thenReturn(1);

        RefreshTokenService.RotationResult rotation = service.rotate(first.token());

        assertEquals(account, rotation.account());
        assertNotEquals(first.token(), rotation.refreshToken().token());
        verify(repository).revokeIfActive(eq(persisted.getTokenHash()), any(Instant.class));
        verify(repository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotationRejectsRequestThatLosesAtomicClaimRace() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);
        String raw = "racing-token";
        String tokenHash = sha256(raw);
        RefreshToken token = new RefreshToken(account, tokenHash, Instant.now().plusSeconds(300));

        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(repository.revokeIfActive(eq(tokenHash), any(Instant.class))).thenReturn(0);

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(raw));
        verify(repository).revokeIfActive(eq(tokenHash), any(Instant.class));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void revokedRefreshTokenCannotBeReused() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);

        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RefreshTokenService.IssuedRefreshToken first = service.issue(account);
        ArgumentCaptor<RefreshToken> created = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(created.capture());
        RefreshToken persisted = created.getValue();
        persisted.revoke();
        when(repository.findByTokenHash(persisted.getTokenHash())).thenReturn(Optional.of(persisted));

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(first.token()));
        verify(repository, never()).revokeIfActive(anyString(), any(Instant.class));
    }

    @Test
    void reuseOfRotatedRefreshTokenRevokesEveryActiveSessionOfThatAccount() {
        // RFC 9700 section 4.14.2: a revoked refresh token presented again means
        // the token leaked (attacker or victim already rotated it). Revoke the
        // account's active refresh sessions so neither party keeps a session.
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        org.springframework.test.util.ReflectionTestUtils.setField(account, "id", 42L);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);
        String raw = "already-rotated-token";
        RefreshToken rotated = new RefreshToken(account, sha256(raw), Instant.now().plusSeconds(300));
        rotated.revoke();
        when(repository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(rotated));

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(raw));
        verify(repository).revokeAllActiveForAccount(eq(42L), any(Instant.class));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void expiredOrUnknownTokensDoNotTriggerAccountWideRevocation() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);
        RefreshToken oldRevoked = new RefreshToken(account, sha256("old"), Instant.now().minusSeconds(1));
        oldRevoked.revoke();
        when(repository.findByTokenHash(sha256("old"))).thenReturn(Optional.of(oldRevoked));
        when(repository.findByTokenHash(sha256("unknown"))).thenReturn(Optional.empty());

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("old"));
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("unknown"));
        verify(repository, never()).revokeAllActiveForAccount(any(), any(Instant.class));
    }

    @Test
    void expiredRefreshTokenCannotBeRotated() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);
        String raw = "expired-token";
        RefreshToken expired = new RefreshToken(account, sha256(raw), Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(expired));

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(raw));
        verify(repository, never()).revokeIfActive(anyString(), any(Instant.class));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void explicitRevokePersistsRevokedTokenAndUnknownTokenIsIdempotent() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);
        String raw = "known-token";
        RefreshToken token = new RefreshToken(account, sha256(raw), Instant.now().plusSeconds(300));
        when(repository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(token));

        service.revoke(raw);

        assertTrue(token.isRevoked());
        verify(repository).save(token);

        reset(repository);
        when(repository.findByTokenHash(sha256("missing-token"))).thenReturn(Optional.empty());
        service.revoke("missing-token");
        verify(repository, never()).save(any(RefreshToken.class));
    }

    private static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
