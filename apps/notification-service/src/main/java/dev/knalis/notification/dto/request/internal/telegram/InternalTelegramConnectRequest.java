package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalTelegramConnectRequest(
        @NotBlank String token,
        @NotNull Long telegramUserId,
        @NotNull Long chatId,
        String username,
        String firstName,
        String lastName
) {
}
