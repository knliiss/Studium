package dev.knalis.content.client.education.http;

import dev.knalis.content.client.education.EducationServiceClient;
import dev.knalis.content.client.education.dto.SubjectResponse;
import dev.knalis.content.client.education.dto.TopicResponse;
import dev.knalis.content.config.ContentEducationServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HttpEducationServiceClient implements EducationServiceClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient educationServiceRestClient;
    private final ContentEducationServiceProperties properties;

    @Override
    public TopicResponse getTopic(UUID topicId) {
        try {
            return educationServiceRestClient.get()
                    .uri("/internal/education/topics/{topicId}", topicId)
                    .header(INTERNAL_SECRET_HEADER, properties.sharedSecret())
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
                    .header(INTERNAL_SECRET_HEADER, properties.sharedSecret())
                    .retrieve()
                    .body(SubjectResponse.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve subjectId=" + subjectId, exception);
        }
    }
}

