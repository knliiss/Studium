package dev.knalis.content.client.education.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        List<UUID> groupIds,
        List<UUID> teacherIds,
        String name,
        String code,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}

