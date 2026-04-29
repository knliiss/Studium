package dev.knalis.notification.service;

import dev.knalis.notification.entity.NotificationCategory;
import dev.knalis.notification.entity.NotificationType;

import java.util.Map;
import java.util.UUID;

public record NotificationDraft(
        UUID userId,
        UUID sourceEventId,
        String sourceEventType,
        NotificationType type,
        NotificationCategory category,
        String title,
        String body,
        Map<String, Object> payload
) {
}
