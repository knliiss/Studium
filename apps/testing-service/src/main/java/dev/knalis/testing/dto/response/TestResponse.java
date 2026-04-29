package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.TestStatus;

import java.time.Instant;
import java.util.UUID;

public record TestResponse(
        UUID id,
        UUID topicId,
        String title,
        int orderIndex,
        TestStatus status,
        int maxAttempts,
        int maxPoints,
        Integer timeLimitMinutes,
        Instant availableFrom,
        Instant availableUntil,
        boolean showCorrectAnswersAfterSubmit,
        boolean shuffleQuestions,
        boolean shuffleAnswers,
        Instant createdAt,
        Instant updatedAt
) {
}
