package io.aetheris.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class AuditEventRepository {
    private static final int RECENT_EVENT_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public AuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveIfAbsent(UserDomainEvent event) {
        jdbcTemplate.update(
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
