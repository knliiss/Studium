package dev.knalis.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardGradeItemResponse(
        UUID gradeId,
        UUID submissionId,
        UUID assignmentId,
        UUID topicId,
        UUID subjectId,
        String assignmentTitle,
        int score,
        String feedback,
        Instant gradedAt
) {
}
