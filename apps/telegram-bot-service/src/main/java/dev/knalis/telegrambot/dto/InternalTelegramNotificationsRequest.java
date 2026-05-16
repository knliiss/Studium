package dev.knalis.telegrambot.dto;

public record InternalTelegramNotificationsRequest(
        Long telegramUserId,
        int limit
) {
}
