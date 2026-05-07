package dev.knalis.content.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TopicMaterialResponse(
        UUID id,
        UUID topicId,
        UUID fileId,
        String title,
        String description,
        boolean visible,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}

