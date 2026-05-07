package dev.knalis.content.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LectureMaterialResponse(
        UUID id,
        UUID lectureId,
        UUID fileId,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}

