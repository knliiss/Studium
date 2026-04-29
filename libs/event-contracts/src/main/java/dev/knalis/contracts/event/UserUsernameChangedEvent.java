package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record UserUsernameChangedEvent(
        UUID eventId,
        UUID userId,
        String oldUsername,
        String newUsername,
        Instant occurredAt
) {
}
