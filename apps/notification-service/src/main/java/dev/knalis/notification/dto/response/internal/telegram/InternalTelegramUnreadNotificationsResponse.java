package dev.knalis.notification.dto.response.internal.telegram;

import java.util.List;

public record InternalTelegramUnreadNotificationsResponse(
        List<InternalTelegramUnreadNotificationItemResponse> unreadItems
) {
}
