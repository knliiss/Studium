package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record TestPublishedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID testId,
        UUID topicId,
        String title,
        Instant availableFrom,
        Instant deadline,
        UUID publishedByUserId
) {
}
