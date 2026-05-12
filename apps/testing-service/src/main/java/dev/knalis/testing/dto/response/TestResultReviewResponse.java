package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.TestStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestResultReviewResponse(
        UUID resultId,
        UUID testId,
        UUID attemptId,
        UUID userId,
        String testTitle,
        TestStatus testStatus,
        Integer maxPoints,
        Integer score,
        Integer autoScore,
        Instant attemptStartedAt,
        Instant attemptCompletedAt,
        Integer totalTimeSpentSeconds,
        Instant submittedAt,
        UUID reviewedByUserId,
        Instant reviewedAt,
        List<TestResultQuestionResponse> questions
) {
}
