package io.aetheris.users.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class UserEventPublisher {
    public static final String EXCHANGE = "aetheris.events";
    private final RabbitTemplate rabbitTemplate;
    private final UserOutboxStore outboxStore;
    private final boolean useOutbox;

    @Autowired
    public UserEventPublisher(RabbitTemplate rabbitTemplate, UserOutboxStore outboxStore,
            @Value("${aetheris.outbox.enabled:false}") boolean useOutbox) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxStore = outboxStore;
        this.useOutbox = useOutbox;
    }

    // Isolated legacy unit tests deliberately exercise the previous direct mode.
    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxStore = null;
        this.useOutbox = false;
    }

    public void userCreated(Long id, String name, String email) {
        publish("user.created", id, name, email);
    }

    public void userDeleted(Long id, String name, String email) {
        publish("user.deleted", id, name, email);
    }

    // Only server-owned User.tenantId reaches the tenant event overload.
    // Legacy global ADMIN mutations intentionally emit no tenant attribution.
    public void userCreated(Long id, String name, String email, UUID tenantId) {
        publish("user.created", id, name, email, java.util.Objects.requireNonNull(tenantId));
    }

    public void userDeleted(Long id, String name, String email, UUID tenantId) {
        publish("user.deleted", id, name, email, java.util.Objects.requireNonNull(tenantId));
    }

    private void publish(String eventType, Long id, String name, String email) {
        publish(eventType, id, name, email, null);
    }

    private void publish(String eventType, Long id, String name, String email, UUID tenantId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("occurredAt", Instant.now().toString());
        event.put("userId", id);
        event.put("name", name);
        event.put("email", email);
        if (tenantId != null) {
            event.put("tenantId", tenantId.toString());
        }
        if (useOutbox) {
            // append() enforces a writable Spring transaction; no direct
            // RabbitMQ publish can escape before the User database COMMIT.
            outboxStore.append(eventType, id, name, email, tenantId);
        } else {
            // Legacy compatibility mode remains available until a reviewed,
            // backed-up migration is explicitly enabled by an operator.
            rabbitTemplate.convertAndSend(EXCHANGE, eventType, event);
        }
    }
}
