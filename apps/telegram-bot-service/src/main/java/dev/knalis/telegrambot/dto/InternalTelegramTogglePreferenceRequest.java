package dev.knalis.telegrambot.dto;

public record InternalTelegramTogglePreferenceRequest(
        Long telegramUserId,
        String category
) {
}
