package io.aetheris.users.security;

import io.aetheris.users.UserController;
import io.aetheris.users.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CI-ONLY review: a synthetic signed tenant claim plus real disposable DB
 * binding. The ACTUAL Identity JwtService does not issue tenant_id, and the
 * legacy shared HMAC key is NOT workload identity. This test does not grant
 * any new API access, create migrations, or certify tenant-aware auth.
 */
@EnabledIfSystemProperty(named = "aetheris.signed.binding.synthetic", matches = "true")
@JdbcTest(properties = {"spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SyntheticSignedTenantBindingTest {
    private static final String TEST_SECRET =
        "SYNTHETIC_TENANT_HMAC_KEY_TEST_ONLY_NOT_DEPLOYED_20261027_64_BYTES";
    private static final String WRONG_SECRET =
        "WRONG_SIGNING_KEY_NEVER_USED_BY_REAL_IDENTITY_SERVICE_20261027";
    private static final SecretKey TEST_KEY =
        Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    private static final UUID TENANT_A =
        UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TENANT_B =
        UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired JdbcTemplate jdbc;

    private long user(String email) {
        return jdbc.queryForObject(
            "SELECT id FROM user_stage.users WHERE email = ?", Long.class, email);
    }

    private String token(String secret, Object uid, String tenant, String role,
                         Object scopes, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject("synthetic@example.invalid")
            .claim("uid", uid)
            .claim("tenant_id", tenant)
            .claim("role", role)
            .claim("scopes", scopes)
            .issuedAt(Date.from(now.minusSeconds(10)))
            .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private String consumer(long uid, UUID tenant) {
        return token(TEST_SECRET, uid, tenant.toString(), "API_CONSUMER",
            List.of("users:read"), 300);
    }

    /**
     * A candidate test-only policy, NOT an endpoint: independently verify
     * the signature and essential consumer claims, then query an exact
     * immutable-looking tuple. The DB role may SELECT only in this rehearsal.
     * An actual rollout additionally needs trusted issuer/audience/key custody,
     * authenticated tenant assignment, durable migrations and revocation.
     */
    private boolean admitsSyntheticObject(String jwt, long requestedUserId) {
        if (jwt == null || requestedUserId <= 0) return false;
        final Claims claims;
        try {
            claims = Jwts.parser().verifyWith(TEST_KEY)
                .build().parseSignedClaims(jwt).getPayload();
            Object rawUid = claims.get("uid");
            if (!(rawUid instanceof Long || rawUid instanceof Integer)) return false;
            long uid = ((Number) rawUid).longValue();
            String tenant = claims.get("tenant_id", String.class);
            String role = claims.get("role", String.class);
            Object rawScopes = claims.get("scopes");
            if (uid <= 0 || tenant == null || tenant.length() != 36
                || !"API_CONSUMER".equals(role)
                || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getIssuedAt() == null || claims.getExpiration() == null
                || !(rawScopes instanceof Collection<?> scopes)
                || scopes.size() > 32 || !scopes.contains("users:read")) return false;
            UUID parsedTenant = UUID.fromString(tenant);
            if (!parsedTenant.toString().equals(tenant)) return false;
            if (!claims.getExpiration().after(claims.getIssuedAt())) return false;
            return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_stage.synthetic_user_owner_bindings " +
                "WHERE identity_uid=? AND user_id=? AND tenant_id=?::uuid)",
                Boolean.class, uid, requestedUserId, tenant));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException invalid) {
            return false;
        }
    }

    @Test
    void signedPrincipalRequiresExactTenantAndExplicitUserBinding() {
        long a = user("user-role@example.invalid");
        long b = user("user-two-role@example.invalid");
        assertNotEquals(a, b);
        // Deliberately crossed ownership rows: matching IDs must NOT pass.
        assertTrue(admitsSyntheticObject(consumer(b, TENANT_A), a));
        assertTrue(admitsSyntheticObject(consumer(a, TENANT_B), b));
        assertFalse(admitsSyntheticObject(consumer(a, TENANT_A), a));
        assertFalse(admitsSyntheticObject(consumer(b, TENANT_B), b));
        assertFalse(admitsSyntheticObject(consumer(b, TENANT_B), a));
        assertFalse(admitsSyntheticObject(consumer(a, TENANT_A), b));
        assertFalse(admitsSyntheticObject(consumer(b, TENANT_A), Long.MAX_VALUE));
        assertFalse(admitsSyntheticObject(consumer(b, TENANT_A), -1));
        assertFalse(admitsSyntheticObject(null, a));
    }

    @Test
    void badSignerExpiryRoleScopeAndMalformedTenantNeverGrantBinding() {
        long a = user("user-role@example.invalid");
        long b = user("user-two-role@example.invalid");
        assertFalse(admitsSyntheticObject(
            token(WRONG_SECRET, b, TENANT_A.toString(), "API_CONSUMER",
                List.of("users:read"), 300), a));
        assertFalse(admitsSyntheticObject(
            token(TEST_SECRET, b, TENANT_A.toString(), "API_CONSUMER",
                List.of("users:read"), -5), a));
        assertFalse(admitsSyntheticObject(
            token(TEST_SECRET, b, TENANT_A.toString(), "ADMIN",
                List.of("users:read"), 300), a));
        assertFalse(admitsSyntheticObject(
            token(TEST_SECRET, b, TENANT_A.toString(), "API_CONSUMER",
                List.of("notifications:read"), 300), a));
        assertFalse(admitsSyntheticObject(
            token(TEST_SECRET, b, "not-a-uuid", "API_CONSUMER",
                List.of("users:read"), 300), a));
        assertFalse(admitsSyntheticObject(
            token(TEST_SECRET, b, null, "API_CONSUMER",
                List.of("users:read"), 300), a));
        assertFalse(admitsSyntheticObject(
            token(TEST_SECRET, String.valueOf(b), TENANT_A.toString(), "API_CONSUMER",
                List.of("users:read"), 300), a));
    }

    @Test
    void realServletFilterStillDeniesConsumerEvenWithSignedTenantAndSpoofedHeaders()
        throws Exception {
        UserService service = mock(UserService.class);
        MockMvc api = MockMvcBuilders.standaloneSetup(new UserController(service))
            .addFilters(new DirectApiJwtFilter(TEST_SECRET)).build();
        long a = user("user-role@example.invalid");
        long b = user("user-two-role@example.invalid");
        api.perform(get("/api/users/" + a)
            .header("Authorization", "Bearer " + consumer(b, TENANT_A))
            .header("X-Aetheris-User-Id", String.valueOf(a))
            .header("X-Aetheris-Tenant-Id", TENANT_A.toString())
            .header("X-Aetheris-User-Role", "ADMIN"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
