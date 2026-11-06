package io.aetheris.audit;

import java.time.Instant;
import java.util.UUID;

public record UserDomainEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        Long userId,
        String name,
        String email,
        UUID tenantId
) {
    // Historical persisted events did not contain tenant ownership.
    public UserDomainEvent(String eventId, String eventType, Instant occurredAt,
                           Long userId, String name, String email) {
        this(eventId, eventType, occurredAt, userId, name, email, null);
    }
}
