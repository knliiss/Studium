package dev.knalis.testing.client.education;

import dev.knalis.testing.client.education.dto.GroupMembershipResponse;
import dev.knalis.testing.client.education.dto.GroupStudentUserResponse;
import dev.knalis.testing.client.education.dto.SubjectResponse;
import dev.knalis.testing.client.education.dto.TopicResponse;

import java.util.List;
import java.util.UUID;

public interface EducationServiceClient {
    
    TopicResponse getTopic(UUID topicId);

    SubjectResponse getSubject(UUID subjectId);

    List<GroupStudentUserResponse> getGroupStudents(UUID groupId);

    List<GroupMembershipResponse> getGroupsByUser(UUID userId);

    List<SubjectResponse> getSubjectsByGroup(UUID groupId);

    List<TopicResponse> getTopicsBySubject(UUID subjectId);
}
