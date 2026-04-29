package dev.knalis.gateway.client.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String type,
        String category,
        String title,
        String body,
        String payloadJson,
        boolean read,
        String status,
        UUID sourceEventId,
        String sourceEventType,
        Instant createdAt,
        Instant updatedAt,
        Instant readAt
) {
}
