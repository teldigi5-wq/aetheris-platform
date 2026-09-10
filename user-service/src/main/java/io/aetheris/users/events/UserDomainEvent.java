package io.aetheris.users.events;

import java.time.Instant;

public record UserDomainEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        Long userId,
        String name,
        String email
) {}
