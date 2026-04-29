package dev.knalis.analytics.dto.response;

import dev.knalis.analytics.entity.PerformanceTrend;
import dev.knalis.analytics.entity.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record StudentRiskResponse(
        UUID userId,
        RiskLevel riskLevel,
        PerformanceTrend performanceTrend,
        Double averageScore,
        int activityScore,
        int disciplineScore,
        int missedDeadlinesCount,
        Instant lastActivityAt,
        Instant updatedAt
) {
}
