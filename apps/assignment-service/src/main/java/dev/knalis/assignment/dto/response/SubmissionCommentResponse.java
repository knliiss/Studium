package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SubmissionCommentResponse(
        UUID id,
        UUID submissionId,
        UUID authorUserId,
        String body,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
}
