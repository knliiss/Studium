package dev.knalis.gateway.client.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record StudentAnalyticsResponse(
        UUID userId,
        Double averageScore,
        int assignmentsCreatedCount,
        int assignmentsSubmittedCount,
        int assignmentsLateCount,
        int testsCompletedCount,
        int missedDeadlinesCount,
        int lectureOpenCount,
        int topicOpenCount,
        Instant lastActivityAt,
        int activityScore,
        int disciplineScore,
        String riskLevel,
        String performanceTrend,
        Instant updatedAt
) {
}
