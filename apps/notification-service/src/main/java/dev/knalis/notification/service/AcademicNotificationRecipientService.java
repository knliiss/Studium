package dev.knalis.notification.service;

import dev.knalis.notification.client.education.EducationServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcademicNotificationRecipientService {
    
    private final EducationServiceClient educationServiceClient;
    
    public List<UUID> resolveScheduleRecipients(UUID groupId, UUID teacherId) {
        return resolveScheduleRecipients(groupId, teacherId, null);
    }
    
    public List<UUID> resolveScheduleRecipients(UUID groupId, UUID primaryTeacherId, UUID secondaryTeacherId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        educationServiceClient.getGroupStudents(groupId).stream()
                .map(groupStudent -> groupStudent.userId())
                .forEach(recipients::add);
        if (primaryTeacherId != null) {
            recipients.add(primaryTeacherId);
        }
        if (secondaryTeacherId != null) {
            recipients.add(secondaryTeacherId);
        }
        return List.copyOf(recipients);
    }
    
    public List<UUID> resolveTopicStudentRecipients(UUID topicId) {
        UUID subjectId = educationServiceClient.getTopic(topicId).subjectId();
        UUID groupId = educationServiceClient.getSubject(subjectId).groupId();
        return educationServiceClient.getGroupStudents(groupId).stream()
                .map(groupStudent -> groupStudent.userId())
                .distinct()
                .toList();
    }
}
