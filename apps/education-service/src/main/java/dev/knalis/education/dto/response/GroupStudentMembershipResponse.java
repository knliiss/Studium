package dev.knalis.education.dto.response;

import dev.knalis.education.entity.GroupMemberRole;
import dev.knalis.education.entity.Subgroup;

import java.time.Instant;
import java.util.UUID;

public record GroupStudentMembershipResponse(
        UUID userId,
        GroupMemberRole role,
        Subgroup subgroup,
        int groupMembershipCount,
        Instant createdAt,
        Instant updatedAt
) {
}
