package dev.knalis.analytics.service;

import dev.knalis.analytics.client.education.EducationServiceClient;
import dev.knalis.analytics.client.education.dto.SubjectResponse;
import dev.knalis.analytics.client.education.dto.TopicResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsContextResolver {
    
    private final EducationServiceClient educationServiceClient;
    
    public UUID resolveSubjectId(UUID subjectId, UUID topicId) {
        if (subjectId != null) {
            return subjectId;
        }
        if (topicId == null) {
            return null;
        }
        return getTopic(topicId).subjectId();
    }
    
    public UUID resolveGroupId(UUID groupId, UUID subjectId, UUID topicId) {
        if (groupId != null) {
            return groupId;
        }
        UUID resolvedSubjectId = resolveSubjectId(subjectId, topicId);
        if (resolvedSubjectId == null) {
            return null;
        }
        return getSubject(resolvedSubjectId).groupId();
    }
    
    public List<UUID> resolveGroupStudentUserIds(UUID groupId) {
        if (groupId == null) {
            return List.of();
        }
        return educationServiceClient.getGroupStudents(groupId).stream()
                .map(groupStudent -> groupStudent.userId())
                .distinct()
                .toList();
    }
    
    @Cacheable(cacheNames = "analytics-topic", key = "#topicId")
    public TopicResponse getTopic(UUID topicId) {
        return educationServiceClient.getTopic(topicId);
    }
    
    @Cacheable(cacheNames = "analytics-subject", key = "#subjectId")
    public SubjectResponse getSubject(UUID subjectId) {
        return educationServiceClient.getSubject(subjectId);
    }
}
