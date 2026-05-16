package dev.knalis.telegrambot.dto;

public record InternalTelegramStatusRequest(
        Long telegramUserId,
        Long chatId
) {
}
