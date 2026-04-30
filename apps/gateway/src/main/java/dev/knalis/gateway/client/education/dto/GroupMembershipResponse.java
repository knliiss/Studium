package dev.knalis.gateway.client.education.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupMembershipResponse(
        UUID groupId,
        String role,
        String subgroup,
        Instant createdAt,
        Instant updatedAt
) {
}
