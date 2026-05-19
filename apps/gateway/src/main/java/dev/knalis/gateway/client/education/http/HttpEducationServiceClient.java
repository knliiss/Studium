package dev.knalis.gateway.client.education.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.education.EducationServiceClient;
import dev.knalis.gateway.client.education.dto.EducationAdminOverviewResponse;
import dev.knalis.gateway.client.education.dto.GroupMembershipResponse;
import dev.knalis.gateway.client.education.dto.GroupPageResponse;
import dev.knalis.gateway.client.education.dto.GroupResponse;
import dev.knalis.gateway.client.education.dto.GroupStudentMembershipResponse;
import dev.knalis.gateway.client.education.dto.SearchPageResponse;
import dev.knalis.gateway.client.education.dto.SubjectPageResponse;
import dev.knalis.gateway.client.education.dto.TopicResponse;
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
public class HttpEducationServiceClient implements EducationServiceClient {

    private static final ParameterizedTypeReference<List<GroupMembershipResponse>> GROUP_MEMBERSHIPS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<GroupStudentMembershipResponse>> GROUP_STUDENTS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<TopicResponse>> TOPIC_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient educationServiceWebClient;
    private final ObjectMapper objectMapper;

    public HttpEducationServiceClient(
            @Qualifier("educationServiceWebClient") WebClient educationServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.educationServiceWebClient = educationServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<List<GroupMembershipResponse>> getGroupsByUser(String bearerToken, String requestId, UUID userId) {
        return educationServiceWebClient.get()
                .uri("/api/v1/education/groups/by-user/{userId}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(GROUP_MEMBERSHIPS_TYPE)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
    }

    @Override
    public Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size) {
        return educationServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/education/search")
                        .queryParam("q", query)
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(SearchPageResponse.class)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
    }

    @Override
    public Mono<EducationAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId) {
        return educationServiceWebClient.get()
                .uri("/api/v1/education/dashboard/admin/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(EducationAdminOverviewResponse.class)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
    }

    @Override
    public Mono<SubjectPageResponse> listSubjects(String bearerToken, String requestId, int size) {
        return educationServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/education/subjects")
                        .queryParam("page", 0)
                        .queryParam("size", size)
                        .queryParam("sortBy", "createdAt")
                        .queryParam("direction", "desc")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(SubjectPageResponse.class)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
    }

    @Override
    public Mono<GroupPageResponse> listGroups(String bearerToken, String requestId, int size) {
        return educationServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/education/groups")
                        .queryParam("page", 0)
                        .queryParam("size", size)
                        .queryParam("sortBy", "createdAt")
                        .queryParam("direction", "desc")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(GroupPageResponse.class)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
    }

    @Override
    public Mono<GroupResponse> getGroup(String bearerToken, String requestId, UUID groupId) {
        return educationServiceWebClient.get()
                .uri("/api/v1/education/groups/{groupId}", groupId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(GroupResponse.class)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
    }

    @Override
    public Mono<List<GroupStudentMembershipResponse>> getGroupStudents(String bearerToken, String requestId, UUID groupId) {
        return educationServiceWebClient.get()
                .uri("/api/v1/education/groups/{groupId}/students", groupId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(GROUP_STUDENTS_TYPE)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
    }

    @Override
    public Mono<List<TopicResponse>> getTopicsBySubject(String bearerToken, String requestId, UUID subjectId) {
        return educationServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/education/topics/subject/{subjectId}")
                        .queryParam("page", 0)
                        .queryParam("size", 200)
                        .queryParam("sortBy", "orderIndex")
                        .queryParam("direction", "asc")
                        .build(subjectId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<dev.knalis.gateway.client.education.dto.TopicPageResponse>() {})
                .map(dev.knalis.gateway.client.education.dto.TopicPageResponse::items)
                .onErrorMap(exception -> translateException("Education service request failed", exception));
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
