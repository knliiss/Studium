package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LectureAttachmentResponse(
        UUID id,
        UUID lectureId,
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

