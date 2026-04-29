package dev.knalis.gateway.client.assignment.dto;

import java.util.List;

public record TeacherAssignmentDashboardResponse(
        List<TeacherSubmissionDashboardItemResponse> pendingSubmissionsToReview,
        List<TeacherSubmissionDashboardItemResponse> recentSubmissions,
        List<TeacherAssignmentDashboardItemResponse> activeAssignments
) {

    public static TeacherAssignmentDashboardResponse empty() {
        return new TeacherAssignmentDashboardResponse(List.of(), List.of(), List.of());
    }
}
