package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.QuestionType;

import java.util.UUID;

public record TestQuestionStatisticsResponse(
        UUID questionId,
        QuestionType questionType,
        String questionText,
        int orderIndex,
        int maxPoints,
        long attemptsCount,
        double averageScore,
        long zeroScoreCount,
        long fullScoreCount
) {
}
