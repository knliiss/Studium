package dev.knalis.education.factory.subject;

import dev.knalis.education.entity.Subject;
import org.springframework.stereotype.Component;

@Component
public class SubjectFactory {
    
    public Subject newSubject(String name, String description) {
        Subject subject = new Subject();
        subject.setGroupId(null);
        subject.setName(name.trim());
        subject.setDescription(description == null || description.isBlank() ? null : description.trim());
        return subject;
    }

    public Subject updateSubject(Subject subject, String name, String description) {
        subject.setName(name.trim());
        subject.setDescription(description == null || description.isBlank() ? null : description.trim());
        return subject;
    }
}
