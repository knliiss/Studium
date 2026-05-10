package dev.knalis.education.dto.response;

import dev.knalis.education.entity.TopicMaterialType;

import java.time.Instant;
import java.util.UUID;

public record TopicMaterialResponse(
        UUID id,
        UUID topicId,
        String title,
        String description,
        TopicMaterialType type,
        String url,
        UUID fileId,
        String originalFileName,
        String contentType,
        Long sizeBytes,
        boolean visible,
        boolean archived,
        int orderIndex,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}

