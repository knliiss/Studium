package dev.knalis.education.dto.response;

import dev.knalis.education.entity.GroupSubgroupMode;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        UUID specialtyId,
        Integer studyYear,
        UUID streamId,
        GroupSubgroupMode subgroupMode,
        Instant createdAt,
        Instant updatedAt
) {
}
