package dev.knalis.schedule.dto.response;

import dev.knalis.schedule.entity.LessonType;

import java.time.Instant;
import java.util.UUID;

public record RoomCapabilityResponse(
        UUID id,
        UUID roomId,
        LessonType lessonType,
        Integer priority,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
