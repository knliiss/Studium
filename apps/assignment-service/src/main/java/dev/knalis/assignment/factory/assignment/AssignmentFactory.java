package dev.knalis.assignment.factory.assignment;

import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Assignment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class AssignmentFactory {
    
    public Assignment newAssignment(
            UUID topicId,
            String title,
            String description,
            Instant deadline,
            AssignmentStatus status,
            boolean allowLateSubmissions,
            int maxSubmissions,
            boolean allowResubmit,
            Set<String> acceptedFileTypes,
            Integer maxFileSizeMb,
            int maxPoints,
            int orderIndex
    ) {
        Assignment assignment = new Assignment();
        assignment.setTopicId(topicId);
        assignment.setTitle(title.trim());
        assignment.setDescription(description == null || description.isBlank() ? null : description.trim());
        assignment.setDeadline(deadline);
        assignment.setStatus(status);
        assignment.setAllowLateSubmissions(allowLateSubmissions);
        assignment.setMaxSubmissions(maxSubmissions);
        assignment.setAllowResubmit(allowResubmit);
        assignment.setAcceptedFileTypes(normalizeAcceptedFileTypes(acceptedFileTypes));
        assignment.setMaxFileSizeMb(maxFileSizeMb);
        assignment.setMaxPoints(maxPoints);
        assignment.setOrderIndex(orderIndex);
        return assignment;
    }

    private Set<String> normalizeAcceptedFileTypes(Set<String> acceptedFileTypes) {
        if (acceptedFileTypes == null || acceptedFileTypes.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String acceptedFileType : acceptedFileTypes) {
            if (acceptedFileType == null || acceptedFileType.isBlank()) {
                continue;
            }
            normalized.add(acceptedFileType.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }
}
