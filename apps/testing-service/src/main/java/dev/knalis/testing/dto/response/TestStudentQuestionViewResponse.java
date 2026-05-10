package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.QuestionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestStudentQuestionViewResponse(
        UUID id,
        UUID testId,
        String text,
        QuestionType type,
        String description,
        int points,
        int orderIndex,
        boolean required,
        List<TestStudentAnswerOptionResponse> answers,
        String presentationJson,
        Instant createdAt,
        Instant updatedAt
) {
}
