package dev.knalis.notification.dto.response.telegram;

import java.time.Instant;

public record TelegramConnectTokenResponse(
        String token,
        String deepLink,
        Instant expiresAt,
        boolean telegramAvailable
) {
}
