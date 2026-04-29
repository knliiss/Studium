package dev.knalis.profile.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String username,
        String email,
        String displayName,
        String avatarFileKey,
        String locale,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {
}
