package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record InternalTelegramScheduleDayRequest(
        @NotNull Long telegramUserId,
        @NotNull LocalDate date
) {
}
