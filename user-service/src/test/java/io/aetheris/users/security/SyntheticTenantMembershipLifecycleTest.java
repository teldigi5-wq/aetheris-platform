package io.aetheris.users.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.sql.Timestamp;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REVIEW-ONLY, synthetic PostgreSQL proof for a future tenant grant model.
 *
 * This test is deliberately NOT the authorization logic for a real API.
 * The real Identity issuer does not produce authoritative tenant membership;
 * the synthetic membership table has no deployed migration or trusted writer.
 *
 * A signed token's tenant assertion alone is insufficient: an independent
 * unrevoked, unexpired membership and exact object binding are BOTH required.
 * Authorization remains DENIED for consumers in the actual User controller.
 */
@EnabledIfSystemProperty(named = "aetheris.membership.lifecycle.synthetic", matches = "true")
@JdbcTest(properties = {"spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SyntheticTenantMembershipLifecycleTest {
    private static final UUID TENANT_A =
        UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TENANT_B =
        UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    JdbcTemplate jdbc;

    private long userId(String email) {
        Long result = jdbc.queryForObject(
            "SELECT id FROM user_stage.users WHERE email = ?", Long.class, email);
        if (result == null || result <= 0) {
            throw new AssertionError("Synthetic user fixture missing");
        }
        return result;
    }

    /**
     * This query models a future authoritative decision; NOT a production
     * endpoint. The caller's uid and tenant must eventually come from a
     * verified principal AND trusted membership-authority service, never from
     * request headers, a client-provided tenant, or matching numeric IDs.
     */
    private boolean hasActiveSyntheticGrant(long identityUid, long resourceId,
                                             UUID authoritativeTenant) {
        if (identityUid <= 0 || resourceId <= 0 || authoritativeTenant == null) {
            return false;
        }
        Boolean exists = jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM user_stage.synthetic_user_owner_bindings AS binding
              JOIN user_stage.synthetic_identity_tenant_memberships AS membership
                ON membership.identity_uid = binding.identity_uid
               AND membership.tenant_id = binding.tenant_id
              WHERE binding.identity_uid = ?
                AND binding.user_id = ?
                AND binding.tenant_id = ?::uuid
                AND membership.revoked_at IS NULL
                AND membership.expires_at > statement_timestamp()
            )
            """,
            Boolean.class, identityUid, resourceId, authoritativeTenant.toString());
        return Boolean.TRUE.equals(exists);
    }

    private String phase() {
        String value = System.getProperty("aetheris.membership.phase", "");
        if (!value.equals("active") && !value.equals("revoked") &&
            !value.equals("expired")) {
            throw new AssertionError("Explicit synthetic lifecycle phase required");
        }
        return value;
    }

    @Test
    void membershipMustBeActiveAndMatchExactIdentityTenantAndObject() {
        String currentPhase = phase();
        long userA = userId("user-role@example.invalid");
        long userB = userId("user-two-role@example.invalid");

        // The synthetic object mappings intentionally CROSS identity/user IDs.
        // Equality between IdentityAccount.uid and User.id MUST NOT grant.
        assertFalse(hasActiveSyntheticGrant(userA, userA, TENANT_A));
        assertFalse(hasActiveSyntheticGrant(userB, userB, TENANT_B));

        boolean expectedTenantA = currentPhase.equals("active");
        boolean expectedTenantB = !currentPhase.equals("expired");

        assertEquals(expectedTenantA,
            hasActiveSyntheticGrant(userB, userA, TENANT_A),
            "The Tenant A grant must disappear immediately after revocation");
        assertEquals(expectedTenantB,
            hasActiveSyntheticGrant(userA, userB, TENANT_B),
            "The Tenant B grant must disappear after membership expiration");

        assertFalse(hasActiveSyntheticGrant(userA, userB, TENANT_A));
        assertFalse(hasActiveSyntheticGrant(userB, userA, TENANT_B));
        assertFalse(hasActiveSyntheticGrant(userA, userA, TENANT_B));
        assertFalse(hasActiveSyntheticGrant(userB, userB, TENANT_A));
        assertFalse(hasActiveSyntheticGrant(userA, Long.MAX_VALUE, TENANT_B));
        assertFalse(hasActiveSyntheticGrant(Long.MAX_VALUE, userB, TENANT_B));
        assertFalse(hasActiveSyntheticGrant(-1, userA, TENANT_A));
        assertFalse(hasActiveSyntheticGrant(userB, -1, TENANT_A));
        assertFalse(hasActiveSyntheticGrant(userB, userA, null));
    }

    @Test
    void membershipExpiryClockCannotFreezeAtTransactionStart() throws Exception {
        // PostgreSQL CURRENT_TIMESTAMP is fixed for the entire transaction,
        // including Spring's @JdbcTest transaction. On long-running requests,
        // that would accept a membership after its real expiration time.
        assumeTrue("active".equals(phase()), "One temporal proof per CI lifecycle");
        Timestamp deadline = jdbc.queryForObject(
            "SELECT CURRENT_TIMESTAMP + INTERVAL '750 milliseconds'",
            Timestamp.class);
        assertTrue(deadline != null, "Synthetic expiry timestamp required");
        // Use a generous margin to avoid CI scheduler-timing false positives.
        Thread.sleep(1500);
        Boolean staleTransactionTime = jdbc.queryForObject(
            "SELECT ?::timestamptz > CURRENT_TIMESTAMP",
            Boolean.class, deadline);
        Boolean currentStatementTime = jdbc.queryForObject(
            "SELECT ?::timestamptz > statement_timestamp()",
            Boolean.class, deadline);
        assertTrue(Boolean.TRUE.equals(staleTransactionTime),
            "The regression fixture must expose stale transaction time");
        assertFalse(Boolean.TRUE.equals(currentStatementTime),
            "New authorization statements must observe actual expiry");
    }

    @Test
    void runtimeCanReadButCannotMutateAuthoritativeGrantData() {
        assertEquals("user_runtime",
            jdbc.queryForObject("SELECT current_user", String.class));
        for (String relation : new String[] {
            "user_stage.synthetic_user_owner_bindings",
            "user_stage.synthetic_identity_tenant_memberships"
        }) {
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, ?, 'SELECT')",
                Boolean.class, relation)));
            for (String privilege : new String[] {"INSERT", "UPDATE", "DELETE", "TRUNCATE"}) {
                assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT has_table_privilege(current_user, ?, ?)",
                    Boolean.class, relation, privilege)));
            }
        }
    }
}
