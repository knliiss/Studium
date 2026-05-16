package dev.knalis.telegrambot.dto;

public record InternalTelegramContextRequest(
        Long telegramUserId,
        Long chatId,
        String username
) {
}
