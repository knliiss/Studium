package dev.knalis.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardSubmissionItemResponse(
        UUID submissionId,
        UUID assignmentId,
        UUID studentId,
        Instant submittedAt
) {
}
