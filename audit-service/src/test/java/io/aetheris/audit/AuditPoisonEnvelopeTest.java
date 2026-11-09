package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Source-level Audit listener validation: invalid user payloads are poison. */
class AuditPoisonEnvelopeTest {
    private static Map<String, Object> valid() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", UUID.randomUUID().toString());
        m.put("eventType", "user.created");
        m.put("occurredAt", "2026-11-06T12:01:02.123456Z");
        m.put("userId", 42L);
        m.put("name", "Synthetic Safe User");
        m.put("email", "synthetic-qa@example.test");
        return m;
    }

    private static void denied(AuditEventStore store, Map<String, Object> payload) {
        var failure = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> store.consume(payload));
        assertEquals("Rejected malformed User domain event", failure.getMessage());
        assertNull(failure.getCause(), "Never expose source event payloads in a cause");
    }

    @Test
    void malformedPayloadsGoToRejectNoRequeueWithoutRepositoryWrites() {
        var repo = mock(AuditEventRepository.class);
        var store = new AuditEventStore(repo);

        denied(store, null);
        for (String field : List.of("eventId", "eventType", "occurredAt",
                "userId", "name", "email")) {
            var missing = valid();
            missing.remove(field);
            denied(store, missing);
            var nullValue = valid();
            nullValue.put(field, null);
            denied(store, nullValue);
        }
        Map<String, Object> extra = valid();
        extra.put("unreviewedExtra", "not-approved");
        denied(store, extra);

        for (Object type : List.of("user.changed", "USER_LOGIN", 7L, "")) {
            var m = valid(); m.put("eventType", type); denied(store, m);
        }
        for (Object timestamp : List.of("not-a-time", "2026-99-99", 7L,
                "2026-11-06T12:01:02Z\nFORGED")) {
            var m = valid(); m.put("occurredAt", timestamp); denied(store, m);
        }
        for (Object id : List.of(-1L, 0, "42", 42.5, Long.MAX_VALUE + ".0", Map.of("id",42))) {
            var m = valid(); m.put("userId", id); denied(store, m);
        }
        for (Object id : List.of(" ", "bad\nevent", "x".repeat(129), 23)) {
            var m = valid(); m.put("eventId", id); denied(store, m);
        }
        for (Object name : List.of(" ", "x".repeat(201), 1, "bad\nname")) {
            var m = valid(); m.put("name", name); denied(store, m);
        }
        for (Object email : List.of(" ", "x".repeat(321), 1, "bad\rtest@example.test")) {
            var m = valid(); m.put("email", email); denied(store, m);
        }

        var tenant = valid();
        tenant.put("tenantId", "NOT-A-UUID");
        denied(store, tenant);
        var mismatch = valid();
        mismatch.put("tenantId", "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA");
        denied(store, mismatch);
        verifyNoInteractions(repo);
    }

    @Test
    void knownLegacyAndCanonicalCurrentEventsRemainSupported() {
        var repo = mock(AuditEventRepository.class);
        var store = new AuditEventStore(repo);
        var legacy = valid(); legacy.put("eventType", "USER_DELETED");
        store.consume(legacy);
        var scoped = valid();
        scoped.put("tenantId", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        store.consume(scoped);
        verify(repo, times(2)).saveIfAbsent(any(UserDomainEvent.class));
    }

    @Test
    void infrastructureFailureStillPropagatesForBrokerRetryNotPermanentReject() {
        var repo = mock(AuditEventRepository.class);
        doThrow(new DataAccessResourceFailureException("synthetic database unavailable"))
                .when(repo).saveIfAbsent(any(UserDomainEvent.class));
        var store = new AuditEventStore(repo);
        assertThrows(DataAccessResourceFailureException.class,
                () -> store.consume(valid()));
    }
}
