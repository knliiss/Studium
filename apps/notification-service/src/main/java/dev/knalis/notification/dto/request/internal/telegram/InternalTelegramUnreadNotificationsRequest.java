package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InternalTelegramUnreadNotificationsRequest(
        @NotNull Long telegramUserId,
        @Min(1) @Max(20) int limit
) {
}
