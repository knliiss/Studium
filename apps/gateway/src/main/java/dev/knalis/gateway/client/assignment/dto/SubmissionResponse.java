package dev.knalis.gateway.client.assignment.dto;

import java.time.Instant;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        UUID assignmentId,
        UUID userId,
        Integer score,
        String feedback,
        Instant gradedAt,
        boolean reviewed,
        String status,
        int attemptNumber,
        int maxAttempts,
        int remainingAttempts,
        boolean submittedLate,
        String reviewComment,
        Instant reviewedAt,
        UUID reviewedByUserId,
        Instant returnedForRevisionAt,
        Instant revisionDeadline,
        Instant submittedAt,
        Instant updatedAt
) {
}
