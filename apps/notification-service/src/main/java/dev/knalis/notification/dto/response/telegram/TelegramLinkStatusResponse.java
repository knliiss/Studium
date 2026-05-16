package dev.knalis.notification.dto.response.telegram;

import java.time.Instant;

public record TelegramLinkStatusResponse(
        boolean telegramEnabledByConfig,
        boolean telegramAvailable,
        boolean connected,
        boolean pending,
        String botUsername,
        String deepLink,
        Instant tokenExpiresAt,
        String telegramUsername,
        Long telegramUserId,
        Long chatId,
        Instant connectedAt,
        Instant disconnectedAt,
        TelegramPreferencesResponse preferences
) {
}
