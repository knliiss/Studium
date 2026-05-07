package dev.knalis.testing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TestQuestionAnswerResponse(
        UUID id,
        UUID questionId,
        String text,
        Boolean isCorrect,
        Instant createdAt,
        Instant updatedAt
) {
}
