package dev.knalis.testing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AnswerResponse(
        UUID id,
        UUID questionId,
        String text,
        boolean isCorrect,
        Instant createdAt,
        Instant updatedAt
) {
}
