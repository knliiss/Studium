package dev.knalis.testing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TestGroupAvailabilityResponse(
        UUID id,
        UUID testId,
        UUID groupId,
        boolean visible,
        Instant availableFrom,
        Instant availableUntil,
        Instant deadline,
        int maxAttempts,
        Instant createdAt,
        Instant updatedAt
) {
}
