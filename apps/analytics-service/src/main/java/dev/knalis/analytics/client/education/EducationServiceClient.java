package dev.knalis.analytics.client.education;

import dev.knalis.analytics.client.education.dto.GroupStudentUserResponse;
import dev.knalis.analytics.client.education.dto.SubjectResponse;
import dev.knalis.analytics.client.education.dto.TopicResponse;

import java.util.List;
import java.util.UUID;

public interface EducationServiceClient {
    
    TopicResponse getTopic(UUID topicId);
    
    SubjectResponse getSubject(UUID subjectId);
    
    List<GroupStudentUserResponse> getGroupStudents(UUID groupId);
}
