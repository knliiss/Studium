package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
}
