package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.NotNull;

public record InternalTelegramContextRequest(
        @NotNull Long telegramUserId,
        @NotNull Long chatId,
        String username
) {
}
