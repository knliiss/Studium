package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TopicResponse(
        UUID id,
        UUID subjectId,
        String title,
        int orderIndex,
        Instant createdAt,
        Instant updatedAt
) {
}
