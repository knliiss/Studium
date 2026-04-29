package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record UserEmailChangedEvent(
        UUID eventId,
        UUID userId,
        String oldEmail,
        String newEmail,
        Instant occurredAt
) {
}