package io.aetheris.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return identityService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return identityService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return identityService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        identityService.logout(request);
    }

    @ExceptionHandler(IdentityService.EmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> duplicateEmail() {
        return Map.of("timestamp", Instant.now().toString(), "status", 409, "error", "Conflict", "message", "Email already registered");
    }

    @ExceptionHandler(IdentityService.InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, Object> invalidCredentials() {
        return Map.of("timestamp", Instant.now().toString(), "status", 401, "error", "Unauthorized", "message", "Invalid email or password");
    }

    @ExceptionHandler(RefreshTokenService.InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, Object> invalidRefreshToken() {
        return Map.of("timestamp", Instant.now().toString(), "status", 401, "error", "Unauthorized", "message", "Invalid, expired, or already used refresh token");
    }
}
