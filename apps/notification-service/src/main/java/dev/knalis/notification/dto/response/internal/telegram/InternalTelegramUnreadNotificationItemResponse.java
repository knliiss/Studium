package dev.knalis.notification.dto.response.internal.telegram;

import java.time.Instant;
import java.util.UUID;

public record InternalTelegramUnreadNotificationItemResponse(
        UUID id,
        String title,
        String body,
        String type,
        String category,
        Instant createdAt
) {
}
