package dev.knalis.gateway.client.testing.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.testing.TestingServiceClient;
import dev.knalis.gateway.client.testing.dto.SearchPageResponse;
import dev.knalis.gateway.client.testing.dto.StudentTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TeacherTestDashboardResponse;
import dev.knalis.gateway.client.testing.dto.TestingAdminOverviewResponse;
import dev.knalis.gateway.exception.GatewayClientException;
import dev.knalis.gateway.filter.RequestIdFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class HttpTestingServiceClient implements TestingServiceClient {

    private final WebClient testingServiceWebClient;
    private final ObjectMapper objectMapper;

    public HttpTestingServiceClient(
            @Qualifier("testingServiceWebClient") WebClient testingServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.testingServiceWebClient = testingServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size) {
        return testingServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/testing/tests/search")
                        .queryParam("q", query)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(SearchPageResponse.class)
                .onErrorMap(exception -> translateException("Testing service request failed", exception));
    }

    @Override
    public Mono<StudentTestDashboardResponse> getStudentDashboard(String bearerToken, String requestId) {
        return testingServiceWebClient.get()
                .uri("/api/v1/testing/dashboard/student/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(StudentTestDashboardResponse.class)
                .onErrorMap(exception -> translateException("Testing service request failed", exception));
    }

    @Override
    public Mono<TeacherTestDashboardResponse> getTeacherDashboard(String bearerToken, String requestId) {
        return testingServiceWebClient.get()
                .uri("/api/v1/testing/dashboard/teacher/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(TeacherTestDashboardResponse.class)
                .onErrorMap(exception -> translateException("Testing service request failed", exception));
    }

    @Override
    public Mono<TestingAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId) {
        return testingServiceWebClient.get()
                .uri("/api/v1/testing/dashboard/admin/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(TestingAdminOverviewResponse.class)
                .onErrorMap(exception -> translateException("Testing service request failed", exception));
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
            // Use fallback message when downstream response is not JSON.
        }
        return fallbackMessage;
    }
}
