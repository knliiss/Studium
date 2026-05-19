package dev.knalis.gateway.client.testing;

import dev.knalis.gateway.client.testing.dto.TestGroupAvailabilityResponse;
import dev.knalis.gateway.client.testing.dto.TestPageResponse;
import dev.knalis.gateway.client.testing.dto.TestResponse;
import dev.knalis.gateway.client.testing.dto.TestResultPageResponse;
import dev.knalis.gateway.client.testing.dto.TestResultResponse;
import dev.knalis.gateway.client.testing.dto.SearchPageResponse;
import dev.knalis.gateway.client.testing.dto.StudentTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TeacherTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TestingAdminOverviewResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface TestingServiceClient {

    Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size);

    Mono<StudentTestDashboardResponse> getStudentDashboard(String bearerToken, String requestId);

    Mono<TeacherTestDashboardResponse> getTeacherDashboard(String bearerToken, String requestId);

    Mono<TestingAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId);

    Mono<TestPageResponse> getTestsByTopic(String bearerToken, String requestId, UUID topicId, int size);

    Mono<TestResponse> getTest(String bearerToken, String requestId, UUID testId);

    Mono<List<TestGroupAvailabilityResponse>> getTestAvailability(String bearerToken, String requestId, UUID testId);

    Mono<TestResultPageResponse> getResultsByTest(String bearerToken, String requestId, UUID testId, int size);

    Mono<List<TestResultResponse>> getMyResultsByTest(String bearerToken, String requestId, UUID testId);
}
