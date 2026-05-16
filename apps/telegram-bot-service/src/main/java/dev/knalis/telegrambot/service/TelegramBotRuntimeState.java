package dev.knalis.telegrambot.service;

public record TelegramBotRuntimeState(
        boolean telegramEnabled,
        boolean botUsernameConfigured,
        boolean tokenPresent,
        TelegramTokenValidationStatus tokenValidationStatus,
        boolean pollingEnabled,
        boolean notificationServiceUrlConfigured,
        boolean internalSecretPresent
) {
}
