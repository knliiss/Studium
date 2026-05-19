package dev.knalis.gateway.client.assignment.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.assignment.AssignmentServiceClient;
import dev.knalis.gateway.client.assignment.dto.AssignmentAdminOverviewResponse;
import dev.knalis.gateway.client.assignment.dto.AssignmentGroupAvailabilityResponse;
import dev.knalis.gateway.client.assignment.dto.AssignmentPageResponse;
import dev.knalis.gateway.client.assignment.dto.AssignmentResponse;
import dev.knalis.gateway.client.assignment.dto.SearchPageResponse;
import dev.knalis.gateway.client.assignment.dto.StudentAssignmentDashboardResponse;
import dev.knalis.gateway.client.assignment.dto.SubmissionPageResponse;
import dev.knalis.gateway.client.assignment.dto.SubmissionResponse;
import dev.knalis.gateway.client.assignment.dto.TeacherAssignmentDashboardResponse;
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
public class HttpAssignmentServiceClient implements AssignmentServiceClient {

    private static final ParameterizedTypeReference<List<AssignmentGroupAvailabilityResponse>> ASSIGNMENT_AVAILABILITY_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<SubmissionResponse>> SUBMISSION_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient assignmentServiceWebClient;
    private final ObjectMapper objectMapper;

    public HttpAssignmentServiceClient(
            @Qualifier("assignmentServiceWebClient") WebClient assignmentServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.assignmentServiceWebClient = assignmentServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size) {
        return assignmentServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/assignments/search")
                        .queryParam("q", query)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(SearchPageResponse.class)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<StudentAssignmentDashboardResponse> getStudentDashboard(String bearerToken, String requestId) {
        return assignmentServiceWebClient.get()
                .uri("/api/v1/assignments/dashboard/student/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(StudentAssignmentDashboardResponse.class)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<TeacherAssignmentDashboardResponse> getTeacherDashboard(String bearerToken, String requestId) {
        return assignmentServiceWebClient.get()
                .uri("/api/v1/assignments/dashboard/teacher/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(TeacherAssignmentDashboardResponse.class)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<AssignmentAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId) {
        return assignmentServiceWebClient.get()
                .uri("/api/v1/assignments/dashboard/admin/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(AssignmentAdminOverviewResponse.class)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<AssignmentPageResponse> getAssignmentsByTopic(String bearerToken, String requestId, UUID topicId, int size) {
        return assignmentServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/assignments/topic/{topicId}")
                        .queryParam("page", 0)
                        .queryParam("size", size)
                        .queryParam("sortBy", "deadline")
                        .queryParam("direction", "asc")
                        .build(topicId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(AssignmentPageResponse.class)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<AssignmentResponse> getAssignment(String bearerToken, String requestId, UUID assignmentId) {
        return assignmentServiceWebClient.get()
                .uri("/api/v1/assignments/{assignmentId}", assignmentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(AssignmentResponse.class)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<List<AssignmentGroupAvailabilityResponse>> getAssignmentAvailability(String bearerToken, String requestId, UUID assignmentId) {
        return assignmentServiceWebClient.get()
                .uri("/api/v1/assignments/{assignmentId}/availability", assignmentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(ASSIGNMENT_AVAILABILITY_TYPE)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<SubmissionPageResponse> getSubmissionsByAssignment(String bearerToken, String requestId, UUID assignmentId, int size) {
        return assignmentServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/submissions/assignment/{assignmentId}")
                        .queryParam("page", 0)
                        .queryParam("size", size)
                        .queryParam("sortBy", "submittedAt")
                        .queryParam("direction", "desc")
                        .build(assignmentId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(SubmissionPageResponse.class)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
    }

    @Override
    public Mono<List<SubmissionResponse>> getMySubmissionsByAssignment(String bearerToken, String requestId, UUID assignmentId) {
        return assignmentServiceWebClient.get()
                .uri("/api/v1/submissions/assignment/{assignmentId}/mine", assignmentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(SUBMISSION_LIST_TYPE)
                .onErrorMap(exception -> translateException("Assignment service request failed", exception));
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
