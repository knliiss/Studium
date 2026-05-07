package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.QuestionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestQuestionViewResponse(
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
        List<TestQuestionAnswerResponse> answers,
        Instant createdAt,
        Instant updatedAt
) {
}
