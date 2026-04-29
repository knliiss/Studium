package dev.knalis.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardTestItemResponse(
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
