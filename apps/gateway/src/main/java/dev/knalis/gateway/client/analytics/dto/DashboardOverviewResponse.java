package dev.knalis.gateway.client.analytics.dto;

public record DashboardOverviewResponse(
        long totalStudentsTracked,
        long lowRiskStudentsCount,
        long mediumRiskStudentsCount,
        long highRiskStudentsCount,
        double averagePlatformScore,
        double averageDisciplineScore,
        double averageActivityScore,
        long totalMissedDeadlines,
        long totalLateSubmissions
) {
}
