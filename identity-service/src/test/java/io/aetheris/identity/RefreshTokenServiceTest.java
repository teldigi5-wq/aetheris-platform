package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    @Test
    void rotationRevokesPresentedTokenAndIssuesReplacement() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", "hash", Role.API_CONSUMER);
        RefreshTokenService service = new RefreshTokenService(repository, 604800L);

        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken first = service.issue(account);
        ArgumentCaptor<RefreshToken> created = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(created.capture());
        RefreshToken persisted = created.getValue();

        when(repository.findByTokenHash(persisted.getTokenHash())).thenReturn(Optional.of(persisted));
        RefreshTokenService.RotationResult rotation = service.rotate(first.token());

        assertTrue(persisted.isRevoked());
        assertEquals(account, rotation.account());
        assertNotEquals(first.token(), rotation.refreshToken().token());
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
        when(repository.findByTokenHash(persisted.getTokenHash())).thenReturn(Optional.of(persisted));

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate(first.token()));
    }
}
