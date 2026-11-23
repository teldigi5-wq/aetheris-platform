package io.aetheris.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final IdentityAccountRepository repository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final boolean selfRegistrationEnabled;

    public IdentityService(IdentityAccountRepository repository,
                           JwtService jwtService,
                           RefreshTokenService refreshTokenService,
                           @Value("${aetheris.self-registration.enabled:false}") boolean selfRegistrationEnabled) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.selfRegistrationEnabled = selfRegistrationEnabled;
    }

    /**
     * Open self-registration grants API_CONSUMER (orchestrator:read only since the I4 role trim).
     * It stays off unless the operator opts in with AETHERIS_SELF_REGISTRATION_ENABLED=true,
     * so an unknown caller cannot mint authenticated accounts by default (defense in depth).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!selfRegistrationEnabled) {
            throw new RegistrationDisabledException();
        }
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        IdentityAccount account = repository.save(new IdentityAccount(
                request.name().trim(), email, passwordEncoder.encode(request.password()), Role.API_CONSUMER));
        return response(account, refreshTokenService.issue(account));
    }

    @Transactional(readOnly = true)
    public IdentityAccount authenticate(LoginRequest request) {
        IdentityAccount account = repository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return account;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        IdentityAccount account = authenticate(request);
        return response(account, refreshTokenService.issue(account));
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        return response(rotation.account(), rotation.refreshToken());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse response(IdentityAccount account, RefreshTokenService.IssuedRefreshToken refreshToken) {
        return new AuthResponse(
                jwtService.issue(account),
                refreshToken.token(),
                "Bearer",
                jwtService.expirationSeconds(),
                refreshToken.expiresInSeconds(),
                new AccountResponse(account.getId(), account.getName(), account.getEmail(), account.getRole(), account.getRole().scopes()));
    }

    static class EmailAlreadyRegisteredException extends RuntimeException {}
    static class InvalidCredentialsException extends RuntimeException {}
    static class RegistrationDisabledException extends RuntimeException {}
}
