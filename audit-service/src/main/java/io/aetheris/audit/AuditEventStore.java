package io.aetheris.audit;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditEventStore {
    private final AuditEventRepository repository;

    public AuditEventStore(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @RabbitListener(queues = RabbitAuditConfig.USER_AUDIT_QUEUE)
    public void consume(Map<String, Object> message) {
        final UUID tenant;
        try {
            tenant = optionalCanonicalTenantId(message.get("tenantId"));
        } catch (IllegalArgumentException invalid) {
            // Security-invalid message should not loop forever through RabbitMQ.
            // Production deployment MUST configure a restricted dead-letter route.
            throw new AmqpRejectAndDontRequeueException(
                    "Rejected malformed tenant attribution on audit event");
        }
        UserDomainEvent event = new UserDomainEvent(
                String.valueOf(message.get("eventId")),
                String.valueOf(message.get("eventType")),
                Instant.parse(String.valueOf(message.get("occurredAt"))),
                Long.valueOf(String.valueOf(message.get("userId"))),
                String.valueOf(message.get("name")),
                String.valueOf(message.get("email")),
                tenant);
        repository.saveIfAbsent(event);
    }

    private static UUID optionalCanonicalTenantId(Object raw) {
        if (raw == null) {
            return null;
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
