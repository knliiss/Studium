package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record TopicOpenedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        UUID subjectId,
        UUID topicId
) {
}
