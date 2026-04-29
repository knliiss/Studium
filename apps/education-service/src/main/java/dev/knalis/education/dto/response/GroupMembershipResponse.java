package dev.knalis.education.dto.response;

import dev.knalis.education.entity.GroupMemberRole;
import dev.knalis.education.entity.Subgroup;

import java.time.Instant;
import java.util.UUID;

public record GroupMembershipResponse(
        UUID groupId,
        GroupMemberRole role,
        Subgroup subgroup,
        Instant createdAt,
        Instant updatedAt
) {
}
