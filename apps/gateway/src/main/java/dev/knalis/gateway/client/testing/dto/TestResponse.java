package dev.knalis.gateway.client.testing.dto;

import java.time.Instant;
import java.util.UUID;

public record TestResponse(
        UUID id,
        UUID topicId,
        String title,
        String status,
        int maxAttempts,
        int maxPoints,
        Integer timeLimitMinutes,
        Instant availableFrom,
        Instant availableUntil,
        Instant createdAt,
        Instant updatedAt
) {
}
