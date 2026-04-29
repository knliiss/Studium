package dev.knalis.gateway.client.analytics.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.analytics.AnalyticsServiceClient;
import dev.knalis.gateway.client.analytics.dto.DashboardOverviewResponse;
import dev.knalis.gateway.client.analytics.dto.GroupOverviewResponse;
import dev.knalis.gateway.client.analytics.dto.StudentAnalyticsResponse;
import dev.knalis.gateway.client.analytics.dto.StudentRiskResponse;
import dev.knalis.gateway.client.analytics.dto.TeacherAnalyticsResponse;
import dev.knalis.gateway.exception.GatewayClientException;
import dev.knalis.gateway.filter.RequestIdFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class HttpAnalyticsServiceClient implements AnalyticsServiceClient {

    private static final ParameterizedTypeReference<List<GroupOverviewResponse>> GROUP_OVERVIEWS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient analyticsServiceWebClient;
    private final ObjectMapper objectMapper;

    public HttpAnalyticsServiceClient(
            @Qualifier("analyticsServiceWebClient") WebClient analyticsServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.analyticsServiceWebClient = analyticsServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<StudentAnalyticsResponse> getStudentAnalytics(String bearerToken, String requestId, UUID userId) {
        return get(
                bearerToken,
                requestId,
                "/api/v1/analytics/students/{userId}",
                StudentAnalyticsResponse.class,
                userId,
                "Analytics service request failed"
        );
    }

    @Override
    public Mono<StudentRiskResponse> getStudentRisk(String bearerToken, String requestId, UUID userId) {
        return get(
                bearerToken,
                requestId,
                "/api/v1/analytics/students/{userId}/risk",
                StudentRiskResponse.class,
                userId,
                "Analytics service request failed"
        );
    }

    @Override
    public Mono<TeacherAnalyticsResponse> getTeacherAnalytics(String bearerToken, String requestId, UUID teacherId) {
        return get(
                bearerToken,
                requestId,
                "/api/v1/analytics/teachers/{teacherId}",
                TeacherAnalyticsResponse.class,
                teacherId,
                "Analytics service request failed"
        );
    }

    @Override
    public Mono<List<GroupOverviewResponse>> getTeacherGroupsAtRisk(String bearerToken, String requestId, UUID teacherId) {
        return analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/teachers/{teacherId}/groups-at-risk", teacherId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(GROUP_OVERVIEWS_TYPE)
                .onErrorMap(exception -> translateException("Analytics service request failed", exception));
    }

    @Override
    public Mono<DashboardOverviewResponse> getDashboardOverview(String bearerToken, String requestId) {
        return get(
                bearerToken,
                requestId,
                "/api/v1/analytics/dashboard/overview",
                DashboardOverviewResponse.class,
                null,
                "Analytics service request failed"
        );
    }

    private <T> Mono<T> get(
            String bearerToken,
            String requestId,
            String path,
            Class<T> responseType,
            Object pathVariable,
            String fallbackMessage
    ) {
        WebClient.RequestHeadersUriSpec<?> request = analyticsServiceWebClient.get();
        WebClient.RequestHeadersSpec<?> requestSpec = pathVariable == null
                ? request.uri(path)
                : request.uri(path, pathVariable);
        return requestSpec
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(responseType)
                .onErrorMap(exception -> translateException(fallbackMessage, exception));
    }

    private GatewayClientException translateException(String fallbackMessage, Throwable exception) {
        if (exception instanceof GatewayClientException gatewayClientException) {
            return gatewayClientException;
        }
        if (exception instanceof WebClientResponseException webClientResponseException) {
            return new GatewayClientException(
                    resolveHttpStatus(webClientResponseException.getStatusCode().value()),
                    extractMessage(webClientResponseException.getResponseBodyAsString(), fallbackMessage)
            );
        }
        return new GatewayClientException(HttpStatus.BAD_GATEWAY, fallbackMessage);
    }

    private HttpStatus resolveHttpStatus(int statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status != null ? status : HttpStatus.BAD_GATEWAY;
    }

    private String extractMessage(String responseBody, String fallbackMessage) {
        if (responseBody == null || responseBody.isBlank()) {
            return fallbackMessage;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.get("message");
            if (message != null && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
        }
        return fallbackMessage;
    }
}
