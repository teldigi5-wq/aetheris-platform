package io.aetheris.users.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional PostgreSQL outbox for User mutations only. The insert uses the
 * SAME Spring-managed DataSource transaction as the JPA User mutation.
 * Delivery is at-least-once; Audit deduplicates by stable eventId.
 */
@Repository
public class UserOutboxStore {
    private static final int MAX_ATTEMPTS = 12;
    private final JdbcTemplate jdbc;

    public UserOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validateSchema() {
        // Fail startup when opt-in is enabled but the reviewed migration is absent.
        jdbc.query("SELECT event_id FROM user_event_outbox LIMIT 0", rs -> {});
    }

    public UUID append(String eventType, Long userId, String name, String email,
                       UUID tenantId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() ||
                TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Outbox mutation requires a writable database transaction");
        }
        if (!"user.created".equals(eventType) && !"user.deleted".equals(eventType)) {
            throw new IllegalArgumentException("Unsupported outbox event type");
        }
        Objects.requireNonNull(userId, "Persisted User ID required");
        Objects.requireNonNull(name, "User name required");
        Objects.requireNonNull(email, "User email required");
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO user_event_outbox
                    (event_id, event_type, occurred_at, user_id, name, email, tenant_id,
                     state, attempts, available_at)
                VALUES (?, ?, clock_timestamp(), ?, ?, ?, ?, 'pending', 0, clock_timestamp())
                """, eventId, eventType, userId, name, email, tenantId);
        return eventId;
    }

    public Optional<LeasedEvent> leaseNext() {
        // One statement is an atomic claim even across multiple relay JVMs.
        UUID token = UUID.randomUUID();
        var rows = jdbc.query("""
                WITH picked AS (
                    SELECT event_id
                    FROM user_event_outbox
                    WHERE attempts < 12 AND (
                        (state IN ('pending', 'retry') AND available_at <= clock_timestamp())
                        OR (state = 'leased' AND lease_until < clock_timestamp())
                    )
                    ORDER BY available_at, event_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE user_event_outbox o
                SET state = 'leased', attempts = o.attempts + 1,
                    lease_token = ?, lease_until = clock_timestamp() + INTERVAL '30 seconds'
                FROM picked
                WHERE o.event_id = picked.event_id
                RETURNING o.event_id, o.event_type, o.occurred_at,
                          o.user_id, o.name, o.email, o.tenant_id,
                          o.attempts, o.lease_token
                """, (rs, n) -> new LeasedEvent(
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getLong("user_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getObject("tenant_id", UUID.class),
                rs.getInt("attempts"),
                rs.getObject("lease_token", UUID.class)), token);
        return rows.stream().findFirst();
    }

    public boolean markDelivered(LeasedEvent event) {
        return jdbc.update("""
                UPDATE user_event_outbox
                SET state='delivered', delivered_at=clock_timestamp(),
                    lease_token=NULL, lease_until=NULL
                WHERE event_id=? AND state='leased' AND lease_token=?
                  AND lease_until > clock_timestamp()
                """, event.eventId(), event.leaseToken()) == 1;
    }

    public boolean markFailure(LeasedEvent event) {
        int delaySeconds = Math.min(300, 1 << Math.min(8, event.attempts()));
        return jdbc.update("""
                UPDATE user_event_outbox
                SET state=CASE WHEN attempts >= ? THEN 'parked' ELSE 'retry' END,
                    available_at=clock_timestamp() + (? * INTERVAL '1 second'),
                    lease_token=NULL, lease_until=NULL
                WHERE event_id=? AND state='leased' AND lease_token=?
                """, MAX_ATTEMPTS, delaySeconds, event.eventId(), event.leaseToken()) == 1;
    }

    public record LeasedEvent(UUID eventId, String eventType, Instant occurredAt,
                              Long userId, String name, String email, UUID tenantId,
                              int attempts, UUID leaseToken) {
        public Map<String, Object> message() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("eventId", eventId.toString());
            result.put("eventType", eventType);
            result.put("occurredAt", occurredAt.toString());
            result.put("userId", userId);
            result.put("name", name);
            result.put("email", email);
            if (tenantId != null) {
                result.put("tenantId", tenantId.toString());
            }
            return result;
        }
    }
}
