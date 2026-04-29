package dev.knalis.gateway.client.schedule.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.schedule.ScheduleServiceClient;
import dev.knalis.gateway.client.schedule.dto.AcademicSemesterResponse;
import dev.knalis.gateway.client.schedule.dto.LessonSlotResponse;
import dev.knalis.gateway.dto.ResolvedLessonResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class HttpScheduleServiceClient implements ScheduleServiceClient {
    
    private static final ParameterizedTypeReference<List<ResolvedLessonResponse>> RESOLVED_LESSONS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<LessonSlotResponse>> LESSON_SLOTS_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<AcademicSemesterResponse> ACADEMIC_SEMESTER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    
    private final WebClient scheduleServiceWebClient;
    private final ObjectMapper objectMapper;
    
    public HttpScheduleServiceClient(
            @Qualifier("scheduleServiceWebClient") WebClient scheduleServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.scheduleServiceWebClient = scheduleServiceWebClient;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<List<ResolvedLessonResponse>> getGroupWeek(
            String bearerToken,
            String requestId,
            UUID groupId,
            LocalDate startDate
    ) {
        return scheduleServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/schedule/groups/{groupId}/week")
                        .queryParam("startDate", startDate)
                        .build(groupId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(RESOLVED_LESSONS_TYPE)
                .onErrorMap(exception -> translateException("Schedule service request failed", exception));
    }
    
    @Override
    public Mono<List<ResolvedLessonResponse>> getGroupRange(
            String bearerToken,
            String requestId,
            UUID groupId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return scheduleServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/schedule/groups/{groupId}/range")
                        .queryParam("dateFrom", dateFrom)
                        .queryParam("dateTo", dateTo)
                        .build(groupId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(RESOLVED_LESSONS_TYPE)
                .onErrorMap(exception -> translateException("Schedule service request failed", exception));
    }

    @Override
    public Mono<List<ResolvedLessonResponse>> getTeacherRange(
            String bearerToken,
            String requestId,
            UUID teacherId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return scheduleServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/schedule/teachers/{teacherId}/range")
                        .queryParam("dateFrom", dateFrom)
                        .queryParam("dateTo", dateTo)
                        .build(teacherId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(RESOLVED_LESSONS_TYPE)
                .onErrorMap(exception -> translateException("Schedule service request failed", exception));
    }
    
    @Override
    public Mono<AcademicSemesterResponse> getActiveSemester(String bearerToken, String requestId) {
        return scheduleServiceWebClient.get()
                .uri("/api/v1/schedule/semesters/active")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(ACADEMIC_SEMESTER_TYPE)
                .onErrorMap(exception -> translateException("Schedule service request failed", exception));
    }
    
    @Override
    public Mono<List<LessonSlotResponse>> getLessonSlots(String bearerToken, String requestId) {
        return scheduleServiceWebClient.get()
                .uri("/api/v1/schedule/slots")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(LESSON_SLOTS_TYPE)
                .onErrorMap(exception -> translateException("Schedule service request failed", exception));
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
