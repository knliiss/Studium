package dev.knalis.telegrambot.dto;

public record InternalTelegramConnectRequest(
        String token,
        Long telegramUserId,
        Long chatId,
        String username,
        String firstName,
        String lastName
) {
}
