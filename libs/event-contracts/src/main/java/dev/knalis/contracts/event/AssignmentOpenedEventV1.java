package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record AssignmentOpenedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        UUID assignmentId,
        UUID subjectId,
        UUID topicId
) {
}
