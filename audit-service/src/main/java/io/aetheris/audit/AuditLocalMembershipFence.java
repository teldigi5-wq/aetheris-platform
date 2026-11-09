package io.aetheris.audit;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

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
        // The plain ingest JDBC role has *zero* UPDATE privilege on the
        // membership table. PostgreSQL requires UPDATE privilege for a
        // direct SELECT ... FOR SHARE. A fixed SECURITY DEFINER function
        // acquires that lock instead and returns a Boolean admission verdict.
        // Its row lock persists until the outer @Transactional Audit write
        // commits. The ingest role has EXECUTE on this function only.
        Boolean admitted = jdbc.queryForObject(
                "SELECT public.audit_membership_can_ingest(?, ?, ?)",
                Boolean.class, event.tenantId(), event.userId(),
                Timestamp.from(event.occurredAt()));
        if (!Boolean.TRUE.equals(admitted)) throw reject();
    }

    private static AmqpRejectAndDontRequeueException reject() {
        // No tenant/user ID, PII, database error or event content in reason.
        return new AmqpRejectAndDontRequeueException(
                "Local Audit membership admission denied");
    }

}
