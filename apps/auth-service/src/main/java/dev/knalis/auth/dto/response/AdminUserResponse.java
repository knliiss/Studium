package dev.knalis.auth.dto.response;

import dev.knalis.auth.entity.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String username,
        String email,
        Set<Role> roles,
        Instant createdAt,
        Instant updatedAt
) {
}