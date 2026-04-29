package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record DeadlineMissedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        DeadlineMissedEntityTypeV1 entityType,
        UUID entityId,
        UUID subjectId,
        UUID topicId,
        Instant deadline
) {
}
