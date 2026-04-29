package dev.knalis.analytics.dto.response;

import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RiskLevel;

import java.time.Instant;
import java.util.List;
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
        RiskLevel riskLevel,
        PerformanceTrend performanceTrend,
        Instant updatedAt,
        List<StudentGroupProgressResponse> groupProgress
) {
}
