package io.aetheris.audit;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Audit ingestion accepts only the existing, explicitly supported User event
 * envelope. Malformed client-controlled payloads are poison, not transient
 * infrastructure failures. Database/network failures must still be retried.
 */
@Service
public class AuditEventStore {
    private final AuditEventRepository repository;
    private final AuditEventOriginVerifier originVerifier;

    @org.springframework.beans.factory.annotation.Autowired
    public AuditEventStore(AuditEventRepository repository,
                           AuditEventOriginVerifier originVerifier) {
        this.repository = repository;
        this.originVerifier = originVerifier;
    }

    /** Existing tests and legacy startup remain explicitly unsigned. */
    public AuditEventStore(AuditEventRepository repository) {
        this(repository, AuditEventOriginVerifier.disabled());
    }

    @Transactional
    @RabbitListener(queues = RabbitAuditConfig.USER_AUDIT_QUEUE)
    public void consume(Map<String, Object> message) {
        final UserDomainEvent event;
        try {
            event = validatedEvent(message);
            originVerifier.verify(message, event);
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            // A permanent, malformed payload must not cause unbounded broker
            // retries. No PII, raw exception text or full payload is logged.
            // Production operators must provision a restricted dead-letter route.
            throw new AmqpRejectAndDontRequeueException(
                    "Rejected malformed User domain event");
        }
        // Keep persistence outside the parse/reject handler: JDBC outages,
        // deadlocks and similar transient failures remain retriable.
        repository.saveIfAbsent(event);
    }

    private static UserDomainEvent validatedEvent(Map<String, Object> message) {
        if (message == null || message.size() < 6 || message.size() > 8) {
            throw new IllegalArgumentException("Invalid event envelope");
        }
        for (String key : message.keySet()) {
            if (key == null || !List.of("eventId", "eventType", "occurredAt",
                    "userId", "name", "email", "tenantId", "originProof").contains(key)) {
                throw new IllegalArgumentException("Unrecognized event field");
            }
        }
        String id = boundedText(message.get("eventId"), 128);
        String type = boundedText(message.get("eventType"), 128);
        if (!("user.created".equals(type) || "user.deleted".equals(type)
                || "USER_CREATED".equals(type) || "USER_DELETED".equals(type))) {
            throw new IllegalArgumentException("Unexpected event type");
        }
        String occurred = boundedText(message.get("occurredAt"), 40);
        Instant instant = Instant.parse(occurred);
        Object rawId = message.get("userId");
        if (!(rawId instanceof Long || rawId instanceof Integer || rawId instanceof Short)
                || ((Number) rawId).longValue() <= 0) {
            throw new IllegalArgumentException("Invalid user identifier");
        }
        long userId = ((Number) rawId).longValue();
        String name = boundedText(message.get("name"), 200);
        String email = boundedText(message.get("email"), 320);
        UUID tenant = optionalCanonicalTenantId(message.get("tenantId"));
        return new UserDomainEvent(id, type, instant, userId, name, email, tenant);
    }

    private static String boundedText(Object value, int maxLength) {
        if (!(value instanceof String text) || text.isBlank()
                || text.length() > maxLength || text.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Malformed bounded field");
        }
        return text;
    }

    private static UUID optionalCanonicalTenantId(Object raw) {
        if (raw == null) {
            return null; // Retain historical global ADMIN events without tenant.
        }
        if (!(raw instanceof String stringValue)) {
            throw new IllegalArgumentException("Invalid event tenant identifier");
        }
        try {
            UUID id = UUID.fromString(stringValue);
            if (!id.toString().equals(stringValue)) {
                throw new IllegalArgumentException("Non-canonical event tenant identifier");
            }
            return id;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid event tenant identifier");
        }
    }

    public List<UserDomainEvent> recent() {
        return repository.recent();
    }
}
