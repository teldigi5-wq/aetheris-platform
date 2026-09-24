package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
