package dev.knalis.analytics.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GroupOverviewResponse(
        UUID groupId,
        long totalStudentsTracked,
        long lowRiskStudentsCount,
        long mediumRiskStudentsCount,
        long highRiskStudentsCount,
        Double averageScore,
        double averageActivityScore,
        double averageDisciplineScore,
        long totalMissedDeadlines,
        long totalLateSubmissions,
        Instant updatedAt
) {
}
