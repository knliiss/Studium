package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AssignmentGroupAvailabilityResponse(
        UUID id,
        UUID assignmentId,
        UUID groupId,
        boolean visible,
        Instant availableFrom,
        Instant deadline,
        boolean allowLateSubmissions,
        int maxSubmissions,
        boolean allowResubmit,
        Instant createdAt,
        Instant updatedAt
) {
}
