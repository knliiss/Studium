package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StreamResponse(
        UUID id,
        String name,
        UUID specialtyId,
        Integer studyYear,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
