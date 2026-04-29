package dev.knalis.file.dto.response;

import dev.knalis.file.entity.StoredFileAccess;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.entity.StoredFileStatus;

import java.time.Instant;
import java.util.UUID;

public record StoredFileResponse(
        UUID id,
        UUID fileId,
        UUID ownerId,
        String originalFileName,
        String contentType,
        long sizeBytes,
        StoredFileKind fileKind,
        StoredFileAccess access,
        StoredFileAccess visibility,
        StoredFileStatus status,
        boolean previewAvailable,
        Instant createdAt,
        Instant updatedAt,
        Instant lastAccessedAt,
        Instant scanCompletedAt,
        String scanStatusMessage
) {
}
