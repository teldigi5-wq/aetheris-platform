package io.aetheris.audit;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class AuditEventRepository {
    private static final int RECENT_EVENT_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveIfAbsent(UserDomainEvent event) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO audit_events (event_id, event_type, occurred_at, user_id, name, email, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                event.eventId(),
                event.eventType(),
                Timestamp.from(event.occurredAt()),
                event.userId(),
                event.name(),
                event.email(),
                event.tenantId());
        if (inserted == 1) {
            return;
        }
        if (inserted != 0) {
            throw new IllegalStateException("Unexpected Audit insert cardinality");
        }

        // Replaying the SAME event is expected after a broker ACK gap.
        // Reusing an existing eventId for DIFFERENT immutable content must
        // never silently report success or overwrite a prior tenant.
        // An infrastructure failure still propagates normally for retry.
        List<UserDomainEvent> prior = jdbcTemplate.query(
                """
                SELECT event_id, event_type, occurred_at, user_id, name, email, tenant_id
                FROM audit_events WHERE event_id = ?
                """,
                (rs, row) -> new UserDomainEvent(
                        rs.getString("event_id"),
                        rs.getString("event_type"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getObject("tenant_id", UUID.class)),
                event.eventId());

        if (prior.size() != 1) {
            throw new IllegalStateException("Duplicate Audit event not visible for verification");
        }
        UserDomainEvent existing = prior.get(0);
        // PostgreSQL TIMESTAMPTZ has microsecond precision. Sub-microsecond
        // rounding on legacy Java-produced timestamps is not event tampering.
        boolean sameTime = Math.abs(Duration.between(
                existing.occurredAt(), event.occurredAt()).toNanos()) <= 1_000;
        if (!Objects.equals(existing.eventId(), event.eventId())
                || !Objects.equals(existing.eventType(), event.eventType())
                || !sameTime
                || !Objects.equals(existing.userId(), event.userId())
                || !Objects.equals(existing.name(), event.name())
                || !Objects.equals(existing.email(), event.email())
                || !Objects.equals(existing.tenantId(), event.tenantId())) {
            // Permanent conflicting replay: deny and let the broker's
            // operator-only quarantine route receive the poisoned event.
            // The message contains no PII, tenant ID or raw payload.
            throw new AmqpRejectAndDontRequeueException(
                    "Conflicting immutable Audit event identifier");
        }
    }

    public List<UserDomainEvent> recent() {
        return jdbcTemplate.query(
                """
                SELECT event_id, event_type, occurred_at, user_id, name, email, tenant_id
                FROM audit_events
                ORDER BY occurred_at DESC, event_id DESC
                LIMIT ?
                """,
                (resultSet, rowNum) -> new UserDomainEvent(
                        resultSet.getString("event_id"),
                        resultSet.getString("event_type"),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getLong("user_id"),
                        resultSet.getString("name"),
                        resultSet.getString("email"),
                        resultSet.getObject("tenant_id", UUID.class)),
                RECENT_EVENT_LIMIT);
    }
}
