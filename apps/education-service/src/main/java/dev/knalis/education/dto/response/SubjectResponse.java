package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String name,
        UUID groupId,
        List<UUID> groupIds,
        List<UUID> teacherIds,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
