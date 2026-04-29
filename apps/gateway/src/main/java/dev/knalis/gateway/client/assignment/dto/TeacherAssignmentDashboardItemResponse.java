package dev.knalis.gateway.client.assignment.dto;

import java.time.Instant;
import java.util.UUID;

public record TeacherAssignmentDashboardItemResponse(
        UUID assignmentId,
        UUID topicId,
        UUID subjectId,
        String title,
        Instant deadline,
        String status
) {
}
