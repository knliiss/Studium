package dev.knalis.testing.client.education.dto;

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
