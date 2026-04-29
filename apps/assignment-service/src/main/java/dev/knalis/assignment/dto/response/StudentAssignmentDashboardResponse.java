package dev.knalis.assignment.dto.response;

import java.util.List;

public record StudentAssignmentDashboardResponse(
        List<StudentAssignmentDashboardItemResponse> upcomingDeadlines,
        List<StudentAssignmentDashboardItemResponse> pendingAssignments,
        List<StudentAssignmentGradeItemResponse> recentGrades
) {
}
