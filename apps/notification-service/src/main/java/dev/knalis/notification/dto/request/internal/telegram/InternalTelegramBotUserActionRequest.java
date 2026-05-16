package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InternalTelegramBotUserActionRequest(
        @NotNull UUID linkId
) {
}
