package dev.knalis.gateway.client.education.dto;

import java.util.UUID;

public record GroupStudentMembershipResponse(
        UUID userId,
        String role,
        String subgroup,
        int groupMembershipCount
) {
}
