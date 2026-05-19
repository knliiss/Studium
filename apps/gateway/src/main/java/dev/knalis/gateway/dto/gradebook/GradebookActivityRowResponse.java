package dev.knalis.gateway.dto.gradebook;

import java.time.Instant;
import java.util.UUID;

public record GradebookActivityRowResponse(
        UUID activityId,
        String activityType,
        UUID subjectId,
        UUID topicId,
        String title,
        Integer score,
        int maxPoints,
        Double percentage,
        String status,
        Instant submittedAt,
        Instant reviewedAt,
        Instant dueAt,
        boolean late,
        UUID submissionId,
        UUID resultId
) {
}
