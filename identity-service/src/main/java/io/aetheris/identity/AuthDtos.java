package io.aetheris.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {}

record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}

record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, AccountResponse account) {}
record AccountResponse(Long id, String name, String email, Role role) {}
