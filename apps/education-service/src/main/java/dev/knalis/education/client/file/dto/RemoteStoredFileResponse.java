package dev.knalis.education.client.file.dto;

import java.util.UUID;

public record RemoteStoredFileResponse(
        UUID id,
        UUID ownerId,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String fileKind,
        String access,
        String status,
        boolean previewAvailable,
        String createdAt,
        String updatedAt,
        String lastAccessedAt
) {
}

