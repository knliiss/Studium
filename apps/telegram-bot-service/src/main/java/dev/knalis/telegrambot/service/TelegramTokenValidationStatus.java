package dev.knalis.telegrambot.service;

public enum TelegramTokenValidationStatus {
    DISABLED,
    TOKEN_MISSING,
    TOKEN_PLACEHOLDER,
    TOKEN_FORMAT_INVALID,
    TOKEN_UNAUTHORIZED,
    TOKEN_NOT_FOUND,
    TOKEN_API_ERROR,
    VALID
}
