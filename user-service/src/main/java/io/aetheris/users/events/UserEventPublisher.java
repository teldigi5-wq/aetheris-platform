package io.aetheris.users.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class UserEventPublisher {
    public static final String EXCHANGE = "aetheris.events";
    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void userCreated(Long id, String name, String email) {
        publish("user.created", id, name, email);
    }

    public void userDeleted(Long id, String name, String email) {
        publish("user.deleted", id, name, email);
    }

    private void publish(String eventType, Long id, String name, String email) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("occurredAt", Instant.now().toString());
        event.put("userId", id);
        event.put("name", name);
        event.put("email", email);
        rabbitTemplate.convertAndSend(EXCHANGE, eventType, event);
    }
}
