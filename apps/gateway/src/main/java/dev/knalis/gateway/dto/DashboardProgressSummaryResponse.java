package dev.knalis.gateway.dto;

public record DashboardProgressSummaryResponse(
        Double averageScore,
        int activityScore,
        int disciplineScore,
        int assignmentsSubmittedCount,
        int testsCompletedCount,
        int missedDeadlinesCount
) {
}
