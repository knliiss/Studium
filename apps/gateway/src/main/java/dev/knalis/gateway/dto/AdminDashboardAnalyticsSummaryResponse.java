package dev.knalis.gateway.dto;

public record AdminDashboardAnalyticsSummaryResponse(
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
