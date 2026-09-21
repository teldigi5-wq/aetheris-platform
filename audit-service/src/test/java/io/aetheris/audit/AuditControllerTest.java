package io.aetheris.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditControllerTest {

    @Test
    void emptyStoreProducesEmptyResponse() {
        AuditEventStore store = mock(AuditEventStore.class);
        when(store.recent()).thenReturn(List.of());

        assertEquals(List.of(), new AuditController(store).recentEvents());
    }

    @Test
    void populatedStoreIsReturnedWithoutReorderingOrMutation() {
        AuditEventStore store = mock(AuditEventStore.class);
        List<UserDomainEvent> expected = List.of(new UserDomainEvent(
                "evt-1",
                "USER_CREATED",
                Instant.parse("2026-09-21T10:00:00Z"),
                7L,
                "Poojana",
                "poojana@example.com"));
        when(store.recent()).thenReturn(expected);

        assertEquals(expected, new AuditController(store).recentEvents());
    }
}
