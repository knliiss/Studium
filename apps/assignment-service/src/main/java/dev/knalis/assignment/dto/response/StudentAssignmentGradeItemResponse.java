package dev.knalis.assignment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StudentAssignmentGradeItemResponse(
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
