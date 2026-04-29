package dev.knalis.gateway.client.assignment.dto;

import java.time.Instant;
import java.util.UUID;

public record TeacherSubmissionDashboardItemResponse(
        UUID submissionId,
        UUID assignmentId,
        UUID studentId,
        Instant submittedAt
) {
}
