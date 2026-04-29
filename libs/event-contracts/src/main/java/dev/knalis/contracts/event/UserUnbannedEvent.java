package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record UserUnbannedEvent(
        UUID eventId,
        UUID userId,
        String email,
        String username,
        UUID unbannedBy,
        String unbannedByUsername,
        Instant occurredAt
) {
}