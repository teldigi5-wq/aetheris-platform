package io.aetheris.audit;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuditEventStore {
    private final AuditEventRepository repository;

    public AuditEventStore(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @RabbitListener(queues = RabbitAuditConfig.USER_AUDIT_QUEUE)
    public void consume(Map<String, Object> message) {
        UserDomainEvent event = new UserDomainEvent(
                String.valueOf(message.get("eventId")),
                String.valueOf(message.get("eventType")),
                Instant.parse(String.valueOf(message.get("occurredAt"))),
                Long.valueOf(String.valueOf(message.get("userId"))),
                String.valueOf(message.get("name")),
                String.valueOf(message.get("email")));
        repository.saveIfAbsent(event);
    }

    public List<UserDomainEvent> recent() {
        return repository.recent();
    }
}
