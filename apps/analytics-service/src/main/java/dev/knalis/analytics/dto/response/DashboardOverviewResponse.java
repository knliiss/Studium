package dev.knalis.analytics.dto.response;

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
