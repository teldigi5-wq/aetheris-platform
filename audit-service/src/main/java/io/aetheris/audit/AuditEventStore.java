package io.aetheris.audit;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class AuditEventStore {
    private static final int MAX_EVENTS = 100;
    private final Deque<UserDomainEvent> events = new ConcurrentLinkedDeque<>();

    @RabbitListener(queues = RabbitAuditConfig.USER_AUDIT_QUEUE)
    public void consume(UserDomainEvent event) {
        events.addFirst(event);
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }
    }

    public List<UserDomainEvent> recent() {
        return new ArrayList<>(events);
    }
}
