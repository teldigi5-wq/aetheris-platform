package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventStoreTest {

    @Test
    void consumedMessagesAreMappedAndPersisted() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditEventStore store = new AuditEventStore(repository);

        store.consume(message(
                "evt-2",
                "USER_DELETED",
                Instant.parse("2026-09-21T10:01:00Z"),
                2L,
                "Two",
                "two@example.com"));

        ArgumentCaptor<UserDomainEvent> eventCaptor = ArgumentCaptor.forClass(UserDomainEvent.class);
        verify(repository).saveIfAbsent(eventCaptor.capture());

        UserDomainEvent persisted = eventCaptor.getValue();
        assertEquals("evt-2", persisted.eventId());
        assertEquals("USER_DELETED", persisted.eventType());
        assertEquals(Instant.parse("2026-09-21T10:01:00Z"), persisted.occurredAt());
        assertEquals(2L, persisted.userId());
        assertEquals("Two", persisted.name());
        assertEquals("two@example.com", persisted.email());
    }

    /**
     * Poison messages must be rejected WITHOUT requeue. A parse error thrown from the
     * listener is requeued by default, so one malformed event would be redelivered forever
     * and block the queue.
     */
    @Test
    void malformedMessagesAreRejectedWithoutRequeueAndNeverPersisted() {
        Map<String, Object> valid = message("evt-9", "user.created",
                Instant.parse("2026-09-21T10:01:00Z"), 9L, "Nine", "nine@example.com");
        for (Map.Entry<String, Object> broken : List.of(
                Map.entry("occurredAt", (Object) "not-a-timestamp"),
                Map.entry("userId", (Object) "abc"),
                Map.entry("userId", (Object) 0L),
                Map.entry("eventId", (Object) " "),
                Map.entry("eventType", (Object) ""))) {
            AuditEventRepository repository = mock(AuditEventRepository.class);
            AuditEventStore store = new AuditEventStore(repository);
            Map<String, Object> message = new HashMap<>(valid);
            message.put(broken.getKey(), broken.getValue());
            assertThrows(AmqpRejectAndDontRequeueException.class, () -> store.consume(message),
                    () -> "malformed " + broken.getKey() + " must not be requeued");
            verifyNoInteractions(repository);
        }
        for (String required : List.of("eventId", "eventType", "occurredAt", "userId", "name", "email")) {
            AuditEventRepository repository = mock(AuditEventRepository.class);
            AuditEventStore store = new AuditEventStore(repository);
            Map<String, Object> message = new HashMap<>(valid);
            message.remove(required);
            assertThrows(AmqpRejectAndDontRequeueException.class, () -> store.consume(message),
                    () -> "missing " + required + " must not be requeued or stored as \"null\"");
            verifyNoInteractions(repository);
        }
    }

    @Test
    void recentEventsComeFromDurableRepository() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditEventStore store = new AuditEventStore(repository);
        List<UserDomainEvent> expected = List.of(new UserDomainEvent(
                "evt-1",
                "USER_CREATED",
                Instant.parse("2026-09-21T10:00:00Z"),
                1L,
                "One",
                "one@example.com"));
        when(repository.recent()).thenReturn(expected);

        assertEquals(expected, store.recent());
        verify(repository).recent();
    }

    private static Map<String, Object> message(
            String eventId,
            String eventType,
            Instant occurredAt,
            Long userId,
            String name,
            String email) {
        return Map.of(
                "eventId", eventId,
                "eventType", eventType,
                "occurredAt", occurredAt.toString(),
                "userId", userId,
                "name", name,
                "email", email);
    }
}
