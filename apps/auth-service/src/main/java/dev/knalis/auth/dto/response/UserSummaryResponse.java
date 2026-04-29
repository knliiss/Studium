package dev.knalis.auth.dto.response;

import dev.knalis.auth.entity.Role;

import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String email,
        Set<Role> roles
) {
}
