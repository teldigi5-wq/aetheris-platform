package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityServiceSecurityTest {

    @Test
    void registrationHashesPasswordBeforePersistence() {
        IdentityAccountRepository repository = mock(IdentityAccountRepository.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);

        when(repository.existsByEmailIgnoreCase("poojana@aetheris.local")).thenReturn(false);
        when(repository.save(any(IdentityAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.issue(any())).thenReturn("access-token");
        when(jwtService.expirationSeconds()).thenReturn(3600L);
        when(refreshTokenService.issue(any())).thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 604800L));

        IdentityService service = new IdentityService(repository, jwtService, refreshTokenService);
        service.register(new RegisterRequest("Poojana", "Poojana@Aetheris.Local", "Aetheris123!"));

        ArgumentCaptor<IdentityAccount> accountCaptor = ArgumentCaptor.forClass(IdentityAccount.class);
        verify(repository).save(accountCaptor.capture());
        IdentityAccount persisted = accountCaptor.getValue();

        assertEquals("poojana@aetheris.local", persisted.getEmail());
        assertNotEquals("Aetheris123!", persisted.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("Aetheris123!", persisted.getPasswordHash()));
        assertEquals(Role.API_CONSUMER, persisted.getRole());
    }

    @Test
    void loginRejectsWrongPassword() {
        IdentityAccountRepository repository = mock(IdentityAccountRepository.class);
        JwtService jwtService = mock(JwtService.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        IdentityAccount account = new IdentityAccount("Poojana", "poojana@aetheris.local", encoder.encode("CorrectPassword!"), Role.API_CONSUMER);
        when(repository.findByEmailIgnoreCase("poojana@aetheris.local")).thenReturn(Optional.of(account));

        IdentityService service = new IdentityService(repository, jwtService, refreshTokenService);

        assertThrows(IdentityService.InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("poojana@aetheris.local", "WrongPassword!")));
        verifyNoInteractions(jwtService, refreshTokenService);
    }
}
