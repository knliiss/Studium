package dev.knalis.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record LectureOpenedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        UUID subjectId,
        UUID topicId,
        UUID lectureId
) {
}
