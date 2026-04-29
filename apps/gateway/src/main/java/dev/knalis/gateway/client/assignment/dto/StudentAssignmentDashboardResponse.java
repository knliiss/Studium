package dev.knalis.gateway.client.assignment.dto;

import java.util.List;

public record StudentAssignmentDashboardResponse(
        List<StudentAssignmentDashboardItemResponse> upcomingDeadlines,
        List<StudentAssignmentDashboardItemResponse> pendingAssignments,
        List<StudentAssignmentGradeItemResponse> recentGrades
) {
    public static StudentAssignmentDashboardResponse empty() {
        return new StudentAssignmentDashboardResponse(List.of(), List.of(), List.of());
    }
}
