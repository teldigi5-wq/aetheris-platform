package io.aetheris.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {}

record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}

record RefreshRequest(@NotBlank String refreshToken) {}
record LogoutRequest(@NotBlank String refreshToken) {}

record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        long refreshExpiresInSeconds,
        AccountResponse account
) {}

record AccountResponse(Long id, String name, String email, Role role, Set<String> scopes) {}
