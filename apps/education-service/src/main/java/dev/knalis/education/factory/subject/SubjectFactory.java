package dev.knalis.education.factory.subject;

import dev.knalis.education.entity.Subject;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SubjectFactory {
    
    public Subject newSubject(UUID groupId, String name, String description) {
        Subject subject = new Subject();
        subject.setGroupId(groupId);
        subject.setName(name.trim());
        subject.setDescription(description == null || description.isBlank() ? null : description.trim());
        return subject;
    }

    public Subject updateSubject(Subject subject, UUID groupId, String name, String description) {
        subject.setGroupId(groupId);
        subject.setName(name.trim());
        subject.setDescription(description == null || description.isBlank() ? null : description.trim());
        return subject;
    }
}
