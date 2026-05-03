package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.QuestionType;

import java.time.Instant;
import java.util.List;
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
        String configurationJson,
        List<AnswerResponse> answers,
        Instant createdAt,
        Instant updatedAt
) {
}
