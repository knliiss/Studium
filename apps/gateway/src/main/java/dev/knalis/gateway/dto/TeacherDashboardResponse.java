package dev.knalis.gateway.dto;

import java.util.List;

public record TeacherDashboardResponse(
        List<ResolvedLessonResponse> todayLessons,
        List<DashboardSubmissionItemResponse> pendingSubmissionsToReview,
        List<DashboardSubmissionItemResponse> recentSubmissions,
        List<DashboardGroupRiskResponse> groupsAtRisk,
        List<DashboardAssignmentItemResponse> activeAssignments,
        List<DashboardTestItemResponse> activeTests,
        List<DashboardScheduleChangeResponse> upcomingScheduleChanges
) {
}
