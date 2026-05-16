package dev.knalis.notification.dto.response.internal.telegram;

public enum InternalTelegramConnectStatus {
    CONNECTED,
    ALREADY_CONNECTED,
    USER_ALREADY_HAS_LINK,
    LINKED_TO_ANOTHER_ACCOUNT,
    TOKEN_INVALID,
    TOKEN_EXPIRED,
    TOKEN_USED,
    TOKEN_REVOKED
}
