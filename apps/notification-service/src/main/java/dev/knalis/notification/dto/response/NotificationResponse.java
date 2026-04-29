package dev.knalis.notification.dto.response;

import dev.knalis.notification.entity.NotificationCategory;
import dev.knalis.notification.entity.NotificationStatus;
import dev.knalis.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        NotificationType type,
        NotificationCategory category,
        String title,
        String body,
        String payloadJson,
        boolean read,
        NotificationStatus status,
        UUID sourceEventId,
        String sourceEventType,
        Instant createdAt,
        Instant updatedAt,
        Instant readAt
) {
}
