package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String username,
        String email,
        Instant occurredAt
) {
}