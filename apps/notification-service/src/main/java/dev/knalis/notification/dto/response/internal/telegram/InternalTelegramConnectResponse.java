package dev.knalis.notification.dto.response.internal.telegram;

public record InternalTelegramConnectResponse(
        InternalTelegramConnectStatus status,
        String message
) {
}
