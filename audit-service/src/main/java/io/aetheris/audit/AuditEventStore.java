package io.aetheris.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import java.time.Duration;
import java.util.Objects;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuditEventStore {
    private static final Logger log = LoggerFactory.getLogger(AuditEventStore.class);
    private final AuditEventRepository repository;

    public AuditEventStore(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @RabbitListener(queues = RabbitAuditConfig.USER_AUDIT_QUEUE)
    public void consume(Map<String, Object> message) {
        UserDomainEvent event = parse(message);
        if (repository.saveIfAbsent(event)) {
            return;
        }
        // An identical replay is normal after a broker ACK gap. Reusing an event ID for
        // different content must never silently succeed or overwrite the recorded event.
        UserDomainEvent existing = repository.findByEventId(event.eventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Audit event ID conflicted but the recorded event is not visible"));
        if (!sameContent(existing, event)) {
            log.error("Rejected audit event {}: event ID already recorded with different content",
                    event.eventId());
            throw new AmqpRejectAndDontRequeueException("Conflicting audit event ID");
        }
    }

    /**
     * Strict, non-requeueing parse. A malformed message is rejected without requeue so one
     * poison message cannot be redelivered forever; only the event ID is logged (no PII).
     */
    static UserDomainEvent parse(Map<String, Object> message) {
        String eventId = requiredText(message, "eventId", 128);
        try {
            String eventType = requiredText(message, "eventType", 64);
            Instant occurredAt = Instant.parse(requiredText(message, "occurredAt", 64));
            long userId = positiveId(message.get("userId"));
            String name = requiredString(message, "name");
            String email = requiredString(message, "email");
            return new UserDomainEvent(eventId, eventType, occurredAt, userId, name, email);
        } catch (RuntimeException invalid) {
            throw reject(eventId, invalid);
        }
    }

    private static String requiredText(Map<String, Object> message, String key, int maxLength) {
        Object value = message == null ? null : message.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw reject(null, new IllegalArgumentException("invalid or missing " + key));
        }
        return text;
    }

    private static String requiredString(Map<String, Object> message, String key) {
        if (!(message.get(key) instanceof String text)) {
            throw new IllegalArgumentException("invalid or missing " + key);
        }
        return text;
    }

    private static long positiveId(Object value) {
        long id;
        if (value instanceof Long || value instanceof Integer || value instanceof Short) {
            id = ((Number) value).longValue();
        } else if (value instanceof String text && text.matches("[0-9]{1,18}")) {
            id = Long.parseLong(text);
        } else {
            throw new IllegalArgumentException("invalid or missing userId");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("invalid userId");
        }
        return id;
    }

    private static AmqpRejectAndDontRequeueException reject(String eventId, RuntimeException cause) {
        if (cause instanceof AmqpRejectAndDontRequeueException rejected) {
            return rejected;
        }
        log.warn("Rejected malformed audit event {}: {}", eventId == null ? "<none>" : eventId,
                cause.getMessage());
        return new AmqpRejectAndDontRequeueException("Malformed audit event", cause);
    }

    private static boolean sameContent(UserDomainEvent a, UserDomainEvent b) {
        // PostgreSQL TIMESTAMPTZ stores microseconds; sub-microsecond rounding is not tampering.
        boolean sameTime = Duration.between(a.occurredAt(), b.occurredAt()).abs()
                .compareTo(Duration.ofNanos(1_000)) < 0;
        return sameTime
                && Objects.equals(a.eventType(), b.eventType())
                && Objects.equals(a.userId(), b.userId())
                && Objects.equals(a.name(), b.name())
                && Objects.equals(a.email(), b.email());
    }

    public List<UserDomainEvent> recent() {
        return repository.recent();
    }
}
