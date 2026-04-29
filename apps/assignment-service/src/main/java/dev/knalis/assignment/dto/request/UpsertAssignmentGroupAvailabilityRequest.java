package dev.knalis.assignment.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record UpsertAssignmentGroupAvailabilityRequest(

        @NotNull
        UUID groupId,

        Boolean visible,

        Instant availableFrom,

        @NotNull
        @Future
        Instant deadline,

        Boolean allowLateSubmissions,

        @Min(1)
        Integer maxSubmissions,

        Boolean allowResubmit
) {
}
