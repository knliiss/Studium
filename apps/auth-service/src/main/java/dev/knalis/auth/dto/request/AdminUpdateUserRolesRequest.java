package dev.knalis.auth.dto.request;

import dev.knalis.auth.entity.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record AdminUpdateUserRolesRequest(
        @NotEmpty
        Set<Role> roles
) {
}