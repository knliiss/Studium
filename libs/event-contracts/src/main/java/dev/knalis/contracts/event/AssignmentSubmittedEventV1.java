package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record AssignmentSubmittedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID submissionId,
        UUID assignmentId,
        UUID userId,
        UUID subjectId,
        UUID topicId,
        Instant submittedAt,
        Instant deadline,
        boolean wasLate
) {
}
