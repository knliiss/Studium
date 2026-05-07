package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SubmissionAttachmentResponse(
        UUID id,
        UUID submissionId,
        UUID fileId,
        String displayName,
        String originalFileName,
        String contentType,
        long sizeBytes,
        boolean previewAvailable,
        UUID uploadedByUserId,
        Instant createdAt
) {
}
