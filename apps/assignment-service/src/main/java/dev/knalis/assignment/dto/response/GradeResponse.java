package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GradeResponse(
        UUID id,
        UUID submissionId,
        int score,
        String feedback,
        Instant createdAt,
        Instant updatedAt
) {
}
