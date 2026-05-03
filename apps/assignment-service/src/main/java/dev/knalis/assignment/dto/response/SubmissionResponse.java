package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        UUID assignmentId,
        UUID userId,
        UUID fileId,
        SubmissionFileResponse file,
        Integer score,
        String feedback,
        Instant gradedAt,
        boolean reviewed,
        Instant submittedAt,
        Instant updatedAt
) {
}
