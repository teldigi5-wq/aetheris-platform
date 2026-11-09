package io.aetheris.users;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AetherisUserServiceApplicationTests {
    @DynamicPropertySource
    static void registerEphemeralJwtSigningKey(DynamicPropertyRegistry registry) {
        // A randomly generated key exists only in this test context; production remains fail-closed.
        byte[] rawKey = new byte[32];
        new SecureRandom().nextBytes(rawKey);
        String testOnlyKey = Base64.getEncoder().encodeToString(rawKey);
        registry.add("aetheris.jwt.secret", () -> testOnlyKey);
    }

    @Test
    void contextLoads() {}
}
