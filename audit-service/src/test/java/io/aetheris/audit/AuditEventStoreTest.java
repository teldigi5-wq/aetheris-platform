package io.aetheris.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditEventStoreTest {

    @Test
    void consumedMessagesAreMappedAndReturnedNewestFirst() {
        AuditEventStore store = new AuditEventStore();
        store.consume(message("evt-1", "USER_CREATED", Instant.parse("2026-09-21T10:00:00Z"), 1L, "One", "one@example.com"));
        store.consume(message("evt-2", "USER_DELETED", Instant.parse("2026-09-21T10:01:00Z"), 2L, "Two", "two@example.com"));

        List<UserDomainEvent> recent = store.recent();

        assertEquals(2, recent.size());
        assertEquals("evt-2", recent.get(0).eventId());
        assertEquals("USER_DELETED", recent.get(0).eventType());
        assertEquals(2L, recent.get(0).userId());
        assertEquals("Two", recent.get(0).name());
        assertEquals("two@example.com", recent.get(0).email());
        assertEquals("evt-1", recent.get(1).eventId());
    }

    @Test
    void retentionKeepsOnlyNewestOneHundredEvents() {
        AuditEventStore store = new AuditEventStore();
        Instant base = Instant.parse("2026-09-21T00:00:00Z");

        for (int i = 0; i <= 100; i++) {
            store.consume(message("evt-" + i, "USER_UPDATED", base.plusSeconds(i), (long) i, "User " + i, "u" + i + "@example.com"));
        }

        List<UserDomainEvent> recent = store.recent();
        assertEquals(100, recent.size());
        assertEquals("evt-100", recent.get(0).eventId());
        assertEquals("evt-1", recent.get(99).eventId());
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
