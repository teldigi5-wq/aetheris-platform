package io.aetheris.users.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
        UserDomainEvent event = new UserDomainEvent(
                UUID.randomUUID().toString(), eventType, Instant.now(), id, name, email);
        rabbitTemplate.convertAndSend(EXCHANGE, eventType, event);
    }
}
