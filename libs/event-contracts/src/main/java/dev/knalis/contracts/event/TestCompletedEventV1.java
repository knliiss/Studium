package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record TestCompletedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        UUID testId,
        UUID subjectId,
        UUID topicId,
        Integer score,
        Integer maxScore,
        Instant completedAt
) {
}
