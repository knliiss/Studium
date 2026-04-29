package dev.knalis.testing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record UpsertTestGroupAvailabilityRequest(

        @NotNull
        UUID groupId,

        Boolean visible,

        Instant availableFrom,

        Instant availableUntil,

        Instant deadline,

        @Min(1)
        Integer maxAttempts
) {
}
