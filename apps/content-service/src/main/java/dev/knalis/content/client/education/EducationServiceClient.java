package dev.knalis.content.client.education;

import dev.knalis.content.client.education.dto.SubjectResponse;
import dev.knalis.content.client.education.dto.TopicResponse;

import java.util.UUID;

public interface EducationServiceClient {

    TopicResponse getTopic(UUID topicId);

    SubjectResponse getSubject(UUID subjectId);
}

