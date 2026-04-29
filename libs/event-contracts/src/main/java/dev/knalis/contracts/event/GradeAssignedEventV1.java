package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record GradeAssignedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID gradeId,
        UUID submissionId,
        UUID assignmentId,
        UUID studentUserId,
        Integer score,
        String feedback,
        UUID assignedByUserId,
        UUID subjectId,
        UUID topicId
) {
}
