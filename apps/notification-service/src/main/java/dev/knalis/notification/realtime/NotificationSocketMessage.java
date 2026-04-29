package dev.knalis.notification.realtime;

import dev.knalis.notification.dto.response.NotificationResponse;

import java.time.Instant;
import java.util.UUID;

public record NotificationSocketMessage(
        NotificationSocketEventType eventType,
        UUID userId,
        UUID notificationId,
        NotificationResponse notification,
        long unreadCount,
        Instant occurredAt
) {
}
