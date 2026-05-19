package dev.knalis.gateway.client.auth.dto;

import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String email,
        Set<String> roles
) {
}
