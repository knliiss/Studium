package dev.knalis.testing.client.education.http;

import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.client.education.dto.GroupMembershipResponse;
import dev.knalis.testing.client.education.dto.GroupStudentUserResponse;
import dev.knalis.testing.client.education.dto.SubjectResponse;
import dev.knalis.testing.client.education.dto.TopicResponse;
import dev.knalis.testing.config.TestingEducationServiceProperties;
import lombok.RequiredArgsConstructor;
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
    private final TestingEducationServiceProperties properties;
    
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
            GroupStudentUserResponse[] response = educationServiceRestClient.get()
                    .uri("/internal/education/groups/{groupId}/students", groupId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(GroupStudentUserResponse[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve group students for groupId=" + groupId, exception);
        }
    }

    @Override
    public List<GroupMembershipResponse> getGroupsByUser(UUID userId) {
        try {
            GroupMembershipResponse[] response = educationServiceRestClient.get()
                    .uri("/internal/education/users/{userId}/groups", userId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(GroupMembershipResponse[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve group memberships for userId=" + userId, exception);
        }
    }

    @Override
    public List<SubjectResponse> getSubjectsByGroup(UUID groupId) {
        try {
            SubjectResponse[] response = educationServiceRestClient.get()
                    .uri("/internal/education/groups/{groupId}/subjects", groupId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(SubjectResponse[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve subjects for groupId=" + groupId, exception);
        }
    }

    @Override
    public List<TopicResponse> getTopicsBySubject(UUID subjectId) {
        try {
            TopicResponse[] response = educationServiceRestClient.get()
                    .uri("/internal/education/subjects/{subjectId}/topics", subjectId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .retrieve()
                    .body(TopicResponse[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to resolve topics for subjectId=" + subjectId, exception);
        }
    }
}
