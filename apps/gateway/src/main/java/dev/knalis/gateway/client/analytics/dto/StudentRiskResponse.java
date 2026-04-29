package dev.knalis.gateway.client.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public record StudentRiskResponse(
        UUID userId,
        String riskLevel,
        String performanceTrend,
        Double averageScore,
        int activityScore,
        int disciplineScore,
        int missedDeadlinesCount,
        Instant lastActivityAt,
        Instant updatedAt
) {
}
