package dev.knalis.notification.client.education;

import dev.knalis.notification.client.education.dto.GroupStudentUserResponse;
import dev.knalis.notification.client.education.dto.SubjectResponse;
import dev.knalis.notification.client.education.dto.TopicResponse;

import java.util.List;
import java.util.UUID;

public interface EducationServiceClient {
    
    List<GroupStudentUserResponse> getGroupStudents(UUID groupId);
    
    TopicResponse getTopic(UUID topicId);
    
    SubjectResponse getSubject(UUID subjectId);
}
