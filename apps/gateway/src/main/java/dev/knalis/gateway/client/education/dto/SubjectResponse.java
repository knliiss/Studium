package dev.knalis.gateway.client.education.dto;

import java.util.List;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String name,
        UUID groupId,
        List<UUID> groupIds,
        List<UUID> teacherIds,
        String description
) {
}
