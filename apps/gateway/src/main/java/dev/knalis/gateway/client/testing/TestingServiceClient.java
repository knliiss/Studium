package dev.knalis.gateway.client.testing;

import dev.knalis.gateway.client.testing.dto.SearchPageResponse;
import dev.knalis.gateway.client.testing.dto.StudentTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TeacherTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TestingAdminOverviewResponse;
import reactor.core.publisher.Mono;

public interface TestingServiceClient {

    Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size);

    Mono<StudentTestDashboardResponse> getStudentDashboard(String bearerToken, String requestId);

    Mono<TeacherTestDashboardResponse> getTeacherDashboard(String bearerToken, String requestId);

    Mono<TestingAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId);
}
