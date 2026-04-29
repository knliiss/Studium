package dev.knalis.testing.dto.response;

import dev.knalis.testing.entity.TestStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentTestDashboardItemResponse(
        UUID testId,
        UUID topicId,
        UUID subjectId,
        String title,
        TestStatus status,
        Instant availableFrom,
        Instant availableUntil,
        Integer timeLimitMinutes,
        int attemptsUsed,
        int maxAttempts
) {
}
