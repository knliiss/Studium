package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.NotNull;

public record InternalTelegramMarkAllReadRequest(
        @NotNull Long telegramUserId
) {
}
