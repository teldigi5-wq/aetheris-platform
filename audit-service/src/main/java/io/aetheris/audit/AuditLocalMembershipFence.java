package io.aetheris.audit;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Opt-in local membership *mirror*, not an independent membership authority.
 *
 * Must execute in the SAME @Transactional AuditEventStore.consume transaction
 * as repository.saveIfAbsent. FOR SHARE serializes a membership revocation
 * update against the local event effect. Missing membership fails closed.
 */
@Component
public final class AuditLocalMembershipFence {
    private final boolean required;
    private final JdbcTemplate jdbc;

    @Autowired
    public AuditLocalMembershipFence(
            @Value("${aetheris.audit-membership.local-fence-required:false}") boolean required,
            JdbcTemplate jdbc) {
        if (required && jdbc == null) {
            throw new IllegalArgumentException("Local Audit membership fence requires database");
        }
        this.required = required;
        this.jdbc = jdbc;
    }

    static AuditLocalMembershipFence disabled() {
        return new AuditLocalMembershipFence(false, null);
    }

    boolean isRequired() {
        return required;
    }

    public void enforce(UserDomainEvent event) {
        if (!required) return;
        if (event == null || event.tenantId() == null || event.userId() <= 0) {
            throw reject();
        }
        List<Grant> grants = jdbc.query(
                """
                SELECT status, valid_since, revision
                FROM audit_user_tenant_membership_fence
                WHERE tenant_id = ? AND user_id = ?
                FOR SHARE
                """,
                (rs, row) -> new Grant(
                        rs.getString("status"),
                        rs.getTimestamp("valid_since").toInstant(),
                        rs.getLong("revision")),
                event.tenantId(),
                event.userId());
        if (grants.size() != 1) throw reject();
        Grant grant = grants.get(0);
        if (!"ACTIVE".equals(grant.status())
                || grant.revision() < 1
                || event.occurredAt().isBefore(grant.validSince())) {
            throw reject();
        }
        // Local transaction holds FOR SHARE until Audit event is committed.
        // A concurrently writing REVOKED update must wait for this commit.
    }

    private static AmqpRejectAndDontRequeueException reject() {
        // No tenant/user ID, PII, database error or event content in reason.
        return new AmqpRejectAndDontRequeueException(
                "Local Audit membership admission denied");
    }

    private record Grant(String status, Instant validSince, long revision) {}
}
