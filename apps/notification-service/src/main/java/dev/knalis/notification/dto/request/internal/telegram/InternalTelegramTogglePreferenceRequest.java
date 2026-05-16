package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalTelegramTogglePreferenceRequest(
        @NotNull Long telegramUserId,
        @NotBlank String category
) {
}
