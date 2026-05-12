package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.QuestionType;

import java.time.Instant;
import java.util.UUID;

public record TestResultQuestionResponse(
        UUID id,
        UUID resultId,
        UUID questionId,
        QuestionType questionType,
        String questionText,
        int questionOrderIndex,
        int maxPoints,
        String submittedValueJson,
        String correctValueJson,
        int autoScore,
        int score,
        String reviewComment,
        UUID reviewedByUserId,
        Instant reviewedAt,
        Integer timeSpentSeconds,
        Instant createdAt,
        Instant updatedAt
) {
}
