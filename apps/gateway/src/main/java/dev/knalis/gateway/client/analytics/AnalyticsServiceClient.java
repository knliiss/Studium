package dev.knalis.gateway.client.analytics;

import dev.knalis.gateway.client.analytics.dto.DashboardOverviewResponse;
import dev.knalis.gateway.client.analytics.dto.GroupOverviewResponse;
import dev.knalis.gateway.client.analytics.dto.StudentAnalyticsResponse;
import dev.knalis.gateway.client.analytics.dto.StudentRiskResponse;
import dev.knalis.gateway.client.analytics.dto.TeacherAnalyticsResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface AnalyticsServiceClient {

    Mono<StudentAnalyticsResponse> getStudentAnalytics(String bearerToken, String requestId, UUID userId);

    Mono<StudentRiskResponse> getStudentRisk(String bearerToken, String requestId, UUID userId);

    Mono<TeacherAnalyticsResponse> getTeacherAnalytics(String bearerToken, String requestId, UUID teacherId);

    Mono<List<GroupOverviewResponse>> getTeacherGroupsAtRisk(String bearerToken, String requestId, UUID teacherId);

    Mono<DashboardOverviewResponse> getDashboardOverview(String bearerToken, String requestId);
}
