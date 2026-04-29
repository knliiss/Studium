package dev.knalis.gateway.client.testing.dto;

import java.time.Instant;
import java.util.UUID;

public record StudentTestDashboardItemResponse(
        UUID testId,
        UUID topicId,
        UUID subjectId,
        String title,
        String status,
        Instant availableFrom,
        Instant availableUntil,
        Integer timeLimitMinutes,
        int attemptsUsed,
        int maxAttempts
) {
}
