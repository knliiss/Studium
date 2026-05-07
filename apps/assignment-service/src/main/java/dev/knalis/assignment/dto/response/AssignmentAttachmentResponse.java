package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AssignmentAttachmentResponse(
        UUID id,
        UUID assignmentId,
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
