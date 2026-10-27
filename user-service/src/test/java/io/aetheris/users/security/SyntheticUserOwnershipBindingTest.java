package io.aetheris.users.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review-only SYNTHETIC ownership proof. IdentityAccount.id and User.id are
 * independent sequences and numerical equality NEVER establishes ownership.
 * The input principal/tenant here is *synthetic test evidence*, NOT verified
 * by an application JWT or trusted service in production. No endpoints,
 * privileges, migration auto-discovery, or runtime authorization are enabled.
 */
@EnabledIfSystemProperty(named = "aetheris.object.binding.synthetic", matches = "true")
@JdbcTest(properties = { "spring.flyway.enabled=false" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SyntheticUserOwnershipBindingTest {
    private static final UUID TENANT_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired JdbcTemplate jdbc;

    private long userIdFor(String email) {
        return jdbc.queryForObject(
            "SELECT id FROM user_stage.users WHERE email=?", Long.class, email);
    }

    private boolean hasExplicitBinding(long identityUid, long userId, UUID trustedTenant) {
        if (identityUid <= 0 || userId <= 0 || trustedTenant == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM user_stage.synthetic_user_owner_bindings " +
            "WHERE identity_uid=? AND user_id=? AND tenant_id=?::uuid)",
            Boolean.class, identityUid, userId, trustedTenant.toString()));
    }

    @Test
    void explicitTupleMatchesButEqualNumericIdsAndCrossTenantAccessFail() {
        long userA = userIdFor("user-role@example.invalid");
        long userB = userIdFor("user-two-role@example.invalid");
        assertNotEquals(userA, userB);
        // The *other* identity intentionally has the same ID number as user A.
        // Matching numeric IDs must never be mistaken for an ownership grant.
        assertTrue(hasExplicitBinding(userB, userA, TENANT_A));
        assertTrue(hasExplicitBinding(userA, userB, TENANT_B));
        assertFalse(hasExplicitBinding(userA, userA, TENANT_A));
        assertFalse(hasExplicitBinding(userB, userB, TENANT_B));
        assertFalse(hasExplicitBinding(userB, userA, TENANT_B));
        assertFalse(hasExplicitBinding(userA, userB, TENANT_A));
        assertFalse(hasExplicitBinding(userB, userB, TENANT_A));
        assertFalse(hasExplicitBinding(-1, userA, TENANT_A));
        assertFalse(hasExplicitBinding(userB, -1, TENANT_A));
        assertFalse(hasExplicitBinding(userB, userA, null));
        assertFalse(hasExplicitBinding(Long.MAX_VALUE, userA, TENANT_A));
    }
}
