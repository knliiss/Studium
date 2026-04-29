package dev.knalis.gateway.client.schedule.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record LessonSlotResponse(
        UUID id,
        Integer number,
        LocalTime startTime,
        LocalTime endTime,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
