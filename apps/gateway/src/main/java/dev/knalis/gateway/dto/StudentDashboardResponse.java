package dev.knalis.gateway.dto;

import java.util.List;

public record StudentDashboardResponse(
        List<ResolvedLessonResponse> todaySchedule,
        List<DashboardDeadlineItemResponse> upcomingDeadlines,
        List<DashboardGradeItemResponse> recentGrades,
        long unreadNotificationsCount,
        DashboardProgressSummaryResponse progressSummary,
        String riskLevel,
        List<DashboardAssignmentItemResponse> pendingAssignments,
        List<DashboardTestItemResponse> availableTests
) {
}
