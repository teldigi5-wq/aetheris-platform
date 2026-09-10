package io.aetheris.audit;

import java.time.Instant;

public record UserDomainEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        Long userId,
        String name,
        String email
) {}
