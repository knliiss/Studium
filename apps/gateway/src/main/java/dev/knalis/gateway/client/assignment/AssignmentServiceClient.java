package dev.knalis.gateway.client.assignment;

import dev.knalis.gateway.client.assignment.dto.AssignmentAdminOverviewResponse;
import dev.knalis.gateway.client.assignment.dto.SearchPageResponse;
import dev.knalis.gateway.client.assignment.dto.StudentAssignmentDashboardResponse;
import dev.knalis.gateway.client.assignment.dto.TeacherAssignmentDashboardResponse;
import reactor.core.publisher.Mono;

public interface AssignmentServiceClient {

    Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size);

    Mono<StudentAssignmentDashboardResponse> getStudentDashboard(String bearerToken, String requestId);

    Mono<TeacherAssignmentDashboardResponse> getTeacherDashboard(String bearerToken, String requestId);

    Mono<AssignmentAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId);
}
