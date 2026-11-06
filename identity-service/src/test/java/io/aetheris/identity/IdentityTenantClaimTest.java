package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityTenantClaimTest {
    private static final String SECRET = "synthetic-test-only-identity-signing-key-0123456789";

    private IdentityAccount identity(String email) {
        var id = new IdentityAccount("Synthetic", email, "synthetic-hash", Role.API_CONSUMER);
        ReflectionTestUtils.setField(id, "id", 33L);
        return id;
    }

    @Test
    void newlyRegisteredAccountsReceiveIndependentServerGeneratedTenants() {
        IdentityAccount first = identity("first@example.test");
        IdentityAccount second = identity("second@example.test");
        assertNotNull(first.getTenantId());
        assertNotNull(second.getTenantId());
        assertNotEquals(first.getTenantId(), second.getTenantId());
        assertEquals(first.getTenantId(), UUID.fromString(first.getTenantId().toString()));
    }

    @Test
    void identityOnlySignsPersistedTenantAndTenantBoundScopes() {
        IdentityAccount account = identity("first@example.test");
        String token = new JwtService(SECRET, 600).issue(account);
        var parsed = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(JwtService.DEFAULT_ISSUER)
                .requireAudience(JwtService.DEFAULT_AUDIENCE)
                .build().parseSignedClaims(token);
        assertEquals(account.getTenantId().toString(), parsed.getPayload().get("tid"));
        Object scopes = parsed.getPayload().get("scopes");
        assertTrue(scopes instanceof java.util.Collection<?>);
        assertTrue(((java.util.Collection<?>) scopes).contains("tenant:users:read"));
        assertTrue(((java.util.Collection<?>) scopes).contains("tenant:users:write"));
        assertFalse(((java.util.Collection<?>) scopes).contains("users:read"));
    }

    @Test
    void historicalAccountWithoutOwnerDoesNotGainGuessedTenant() {
        IdentityAccount old = identity("old@example.test");
        ReflectionTestUtils.setField(old, "tenantId", null);
        String token = new JwtService(SECRET, 600).issue(old);
        var parsed = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token);
        assertNull(parsed.getPayload().get("tid"));
    }

    @Test
    void administratorDoesNotInheritTenantAccountUserScopes() {
        assertFalse(Role.ADMIN.scopes().contains("tenant:users:read"));
        assertFalse(Role.DEVELOPER.scopes().contains("tenant:users:write"));
    }
}
