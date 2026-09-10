package io.aetheris.identity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final IdentityAccountRepository repository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public IdentityService(IdentityAccountRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        IdentityAccount account = repository.save(new IdentityAccount(
                request.name().trim(), email, passwordEncoder.encode(request.password()), Role.API_CONSUMER));
        return response(account);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        IdentityAccount account = repository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return response(account);
    }

    private AuthResponse response(IdentityAccount account) {
        return new AuthResponse(jwtService.issue(account), "Bearer", jwtService.expirationSeconds(),
                new AccountResponse(account.getId(), account.getName(), account.getEmail(), account.getRole()));
    }

    static class EmailAlreadyRegisteredException extends RuntimeException {}
    static class InvalidCredentialsException extends RuntimeException {}
}
