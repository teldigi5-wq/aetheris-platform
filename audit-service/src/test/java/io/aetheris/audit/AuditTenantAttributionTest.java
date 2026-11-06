package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditTenantAttributionTest {
    private static final UUID TENANT =
            UUID.fromString("e398c9b9-23ea-473a-8240-8c42a92f7a34");

    private Map<String,Object> syntheticEvent() {
        Map<String,Object> msg = new LinkedHashMap<>();
        msg.put("eventId", "synthetic-event-1");
        msg.put("eventType", "user.created");
        msg.put("occurredAt", Instant.parse("2026-11-06T06:00:00Z").toString());
        msg.put("userId", 17L);
        msg.put("name", "Synthetic Account");
        msg.put("email", "synthetic@example.test");
        return msg;
    }

    @Test
    void explicitCanonicalEventTenantPersistsAsTypedUuid() {
        var repository = mock(AuditEventRepository.class);
        var store = new AuditEventStore(repository);
        var payload = syntheticEvent();
        payload.put("tenantId", TENANT.toString());
        store.consume(payload);
        var captor = org.mockito.ArgumentCaptor.forClass(UserDomainEvent.class);
        verify(repository).saveIfAbsent(captor.capture());
        assertEquals(TENANT, captor.getValue().tenantId());
        assertEquals("user.created", captor.getValue().eventType());
        assertEquals(17L, captor.getValue().userId());
    }

    @Test
    void legacyEventRemainsWithoutTenantAndIsNotAssignedByUserId() {
        var repository = mock(AuditEventRepository.class);
        var store = new AuditEventStore(repository);
        store.consume(syntheticEvent());
        var captured = org.mockito.ArgumentCaptor.forClass(UserDomainEvent.class);
        verify(repository).saveIfAbsent(captured.capture());
        assertNull(captured.getValue().tenantId());
    }

    @Test
    void nonCanonicalMalformedOrUntrustedTenantValueNeverReachesDatabase() {
        var repository = mock(AuditEventRepository.class);
        var store = new AuditEventStore(repository);
        for(Object value : new Object[] {
                "", "not-a-uuid", TENANT.toString().toUpperCase(),
                "00000000-0000-0000-0000-0", 12L, Map.of("tenantId", TENANT.toString())}) {
            var msg = syntheticEvent();
            msg.put("tenantId", value);
            assertThrows(AmqpRejectAndDontRequeueException.class,
                    () -> store.consume(msg));
        }
        verifyNoInteractions(repository);
    }
}
