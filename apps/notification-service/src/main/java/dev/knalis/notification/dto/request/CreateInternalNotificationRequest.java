package dev.knalis.notification.dto.request;

import dev.knalis.notification.entity.NotificationCategory;
import dev.knalis.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateInternalNotificationRequest(
        @NotNull NotificationType type,
        @NotNull NotificationCategory category,
        @NotBlank String title,
        @NotBlank String body,
        Map<String, Object> payload
) {
}
