package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record UserBannedEvent(
        UUID eventId,
        UUID userId,
        String email,
        String username,
        String reason,
        Instant expiresAt,
        UUID bannedBy,
        String bannedByUsername,
        Instant occurredAt
) {
}