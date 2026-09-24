package io.aetheris.identity;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    @Test
    void rotationRevokesPresentedTokenAndIssuesReplacementUsingLockedLookup() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);

        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken first = service.issue(account);
        ArgumentCaptor<RefreshToken> created = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(created.capture());
        RefreshToken persisted = created.getValue();

        when(repository.findByTokenHashForUpdate(persisted.getTokenHash())).thenReturn(Optional.of(persisted));
        RefreshTokenService.RotationResult rotation = service.rotate(first.token());

        assertTrue(persisted.isRevoked());
        assertEquals(account, rotation.account());
        assertNotEquals(first.token(), rotation.refreshToken().token());
        verify(repository).findByTokenHashForUpdate(persisted.getTokenHash());
        verify(repository, never()).findByTokenHash(anyString());
        verify(repository, times(3)).save(any(RefreshToken.class));
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
        when(repository.findByTokenHashForUpdate(persisted.getTokenHash())).thenReturn(Optional.of(persisted));

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(first.token()));
        verify(repository).findByTokenHashForUpdate(persisted.getTokenHash());
    }

    @Test
    void expiredRefreshTokenCannotBeRotated() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);
        String raw = "expired-token";
        RefreshToken expired = new RefreshToken(account, sha256(raw), Instant.now().minusSeconds(1));
        when(repository.findByTokenHashForUpdate(sha256(raw))).thenReturn(Optional.of(expired));

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(raw));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void explicitRevokeUsesLockedLookupAndUnknownTokenIsIdempotent() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);
        String raw = "known-token";
        RefreshToken token = new RefreshToken(account, sha256(raw), Instant.now().plusSeconds(300));
        when(repository.findByTokenHashForUpdate(sha256(raw))).thenReturn(Optional.of(token));

        service.revoke(raw);

        assertTrue(token.isRevoked());
        verify(repository).findByTokenHashForUpdate(sha256(raw));
        verify(repository).save(token);

        reset(repository);
        when(repository.findByTokenHashForUpdate(sha256("missing-token"))).thenReturn(Optional.empty());
        service.revoke("missing-token");
        verify(repository).findByTokenHashForUpdate(sha256("missing-token"));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotationLookupDeclaresPessimisticWriteLock() throws Exception {
        Method method = RefreshTokenRepository.class.getMethod("findByTokenHashForUpdate", String.class);
        Lock lock = method.getAnnotation(Lock.class);
        Query query = method.getAnnotation(Query.class);

        assertNotNull(lock, "refresh rotation lookup must carry a database lock");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
        assertNotNull(query, "locked lookup must use an explicit token-hash query");
        assertTrue(query.value().contains("token.tokenHash = :tokenHash"));
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
