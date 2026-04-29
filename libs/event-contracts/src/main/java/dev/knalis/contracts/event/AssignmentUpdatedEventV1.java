package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record AssignmentUpdatedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID assignmentId,
        UUID topicId,
        String title,
        Instant deadline,
        AssignmentImportantChangeTypeV1 importantChangeType,
        UUID updatedByUserId
) {
}
