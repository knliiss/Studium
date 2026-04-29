package dev.knalis.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardAssignmentItemResponse(
        UUID assignmentId,
        UUID topicId,
        UUID subjectId,
        String title,
        Instant deadline,
        String status,
        boolean submitted
) {
}
