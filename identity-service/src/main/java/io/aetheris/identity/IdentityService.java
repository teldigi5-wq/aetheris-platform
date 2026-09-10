package io.aetheris.identity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final IdentityAccountRepository repository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public IdentityService(IdentityAccountRepository repository,
                           JwtService jwtService,
                           RefreshTokenService refreshTokenService) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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
                new AccountResponse(account.getId(), account.getName(), account.getEmail(), account.getRole()));
    }

    static class EmailAlreadyRegisteredException extends RuntimeException {}
    static class InvalidCredentialsException extends RuntimeException {}
}
