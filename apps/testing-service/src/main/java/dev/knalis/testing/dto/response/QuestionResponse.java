package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.QuestionType;

import java.time.Instant;
import java.util.UUID;

public record QuestionResponse(
        UUID id,
        UUID testId,
        String text,
        QuestionType type,
        String description,
        int points,
        int orderIndex,
        boolean required,
        String feedback,
        Instant createdAt,
        Instant updatedAt
) {
}
