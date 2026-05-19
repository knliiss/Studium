package dev.knalis.gateway.client.assignment;

import dev.knalis.gateway.client.assignment.dto.AssignmentGroupAvailabilityResponse;
import dev.knalis.gateway.client.assignment.dto.AssignmentPageResponse;
import dev.knalis.gateway.client.assignment.dto.AssignmentResponse;
import dev.knalis.gateway.client.assignment.dto.AssignmentAdminOverviewResponse;
import dev.knalis.gateway.client.assignment.dto.SearchPageResponse;
import dev.knalis.gateway.client.assignment.dto.StudentAssignmentDashboardResponse;
import dev.knalis.gateway.client.assignment.dto.SubmissionPageResponse;
import dev.knalis.gateway.client.assignment.dto.SubmissionResponse;
import dev.knalis.gateway.client.assignment.dto.TeacherAssignmentDashboardResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface AssignmentServiceClient {

    Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size);

    Mono<StudentAssignmentDashboardResponse> getStudentDashboard(String bearerToken, String requestId);

    Mono<TeacherAssignmentDashboardResponse> getTeacherDashboard(String bearerToken, String requestId);

    Mono<AssignmentAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId);

    Mono<AssignmentPageResponse> getAssignmentsByTopic(String bearerToken, String requestId, UUID topicId, int size);

    Mono<AssignmentResponse> getAssignment(String bearerToken, String requestId, UUID assignmentId);

    Mono<List<AssignmentGroupAvailabilityResponse>> getAssignmentAvailability(String bearerToken, String requestId, UUID assignmentId);

    Mono<SubmissionPageResponse> getSubmissionsByAssignment(String bearerToken, String requestId, UUID assignmentId, int size);

    Mono<List<SubmissionResponse>> getMySubmissionsByAssignment(String bearerToken, String requestId, UUID assignmentId);
}
