package io.aetheris.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceSecretTest {

    @Test
    void serviceRefusesToStartWithoutSecret() {
        // application.yml now resolves a missing AETHERIS_JWT_SECRET to "".
        assertThrows(IllegalArgumentException.class, () -> new JwtService("", 3600));
    }

    @Test
    void serviceRefusesFormerRepositoryDefault() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("change-me-local-development-secret-32chars", 3600));
    }
}
