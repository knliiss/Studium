package dev.knalis.schedule.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String code,
        String building,
        Integer floor,
        Integer capacity,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
