package dev.knalis.profile.client.dto;

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
        String createdAt,
        String updatedAt,
        String lastAccessedAt
) {
}
