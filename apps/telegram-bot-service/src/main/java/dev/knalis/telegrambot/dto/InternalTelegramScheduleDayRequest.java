package dev.knalis.telegrambot.dto;

import java.time.LocalDate;

public record InternalTelegramScheduleDayRequest(
        Long telegramUserId,
        LocalDate date
) {
}
