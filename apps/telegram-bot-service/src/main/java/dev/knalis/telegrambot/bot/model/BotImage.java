package dev.knalis.telegrambot.bot.model;

public record BotImage(
        String fileName,
        byte[] bytes
) {
}
