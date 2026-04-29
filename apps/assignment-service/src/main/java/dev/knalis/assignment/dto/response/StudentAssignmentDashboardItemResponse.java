package dev.knalis.assignment.dto.response;

import dev.knalis.assignment.entity.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentAssignmentDashboardItemResponse(
        UUID assignmentId,
        UUID topicId,
        UUID subjectId,
        String title,
        Instant deadline,
        AssignmentStatus status,
        boolean submitted
) {
}
