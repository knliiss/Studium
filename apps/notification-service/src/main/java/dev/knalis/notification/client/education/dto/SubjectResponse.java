package dev.knalis.notification.client.education.dto;

import java.time.Instant;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String name,
        UUID groupId,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
