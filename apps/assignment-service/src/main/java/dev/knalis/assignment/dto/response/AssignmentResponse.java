package dev.knalis.assignment.dto.response;

import dev.knalis.assignment.entity.AssignmentStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID topicId,
        String title,
        String description,
        Instant deadline,
        int orderIndex,
        AssignmentStatus status,
        boolean allowLateSubmissions,
        int maxSubmissions,
        boolean allowResubmit,
        Set<String> acceptedFileTypes,
        Integer maxFileSizeMb,
        Instant createdAt,
        Instant updatedAt
) {
}
