package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SpecialtyResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
