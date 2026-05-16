package dev.knalis.telegrambot.dto;

public record InternalTelegramStatusResponse(
        boolean connected,
        String status
) {
}
