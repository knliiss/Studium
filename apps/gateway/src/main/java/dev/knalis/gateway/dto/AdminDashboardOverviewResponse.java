package dev.knalis.gateway.dto;

import java.util.List;

public record AdminDashboardOverviewResponse(
        long totalStudents,
        long totalTeachers,
        long totalGroups,
        long activeSubjects,
        long highRiskStudentsCount,
        long activeDeadlinesCount,
        List<DashboardAuditEventResponse> recentAuditEvents,
        AdminDashboardAnalyticsSummaryResponse analyticsSummary
) {
}
