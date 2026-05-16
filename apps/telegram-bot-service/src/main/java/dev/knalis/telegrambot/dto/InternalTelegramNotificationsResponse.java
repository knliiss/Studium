package dev.knalis.telegrambot.dto;

import java.util.List;

public record InternalTelegramNotificationsResponse(
        List<InternalTelegramNotificationItem> unreadItems
) {
}
