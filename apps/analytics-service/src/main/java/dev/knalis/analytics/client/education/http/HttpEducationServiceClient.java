package dev.knalis.analytics.client.education.http;

import dev.knalis.analytics.client.education.EducationServiceClient;
import dev.knalis.analytics.client.education.dto.GroupStudentUserResponse;
import dev.knalis.analytics.client.education.dto.SubjectResponse;
import dev.knalis.analytics.client.education.dto.TopicResponse;
import dev.knalis.analytics.config.AnalyticsEducationServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HttpEducationServiceClient implements EducationServiceClient {
    
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    
    private final RestClient educationServiceRestClient;
    private final AnalyticsEducationServiceProperties properties;
    
    @Override
    public TopicResponse getTopic(UUID topicId) {
        try {
            return educationServiceRestClient.get()
                    .uri("/internal/education/topics/{topicId}", topicId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(TopicResponse.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve topicId=" + topicId, exception);
        }
    }
    
    @Override
    public SubjectResponse getSubject(UUID subjectId) {
        try {
            return educationServiceRestClient.get()
                    .uri("/internal/education/subjects/{subjectId}", subjectId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(SubjectResponse.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve subjectId=" + subjectId, exception);
        }
    }
    
    @Override
    public List<GroupStudentUserResponse> getGroupStudents(UUID groupId) {
        try {
            List<GroupStudentUserResponse> response = educationServiceRestClient.get()
                    .uri("/internal/education/groups/{groupId}/students", groupId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null ? List.of() : response;
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve students for groupId=" + groupId, exception);
        }
    }
}
