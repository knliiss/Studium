package dev.knalis.notification.dto.response.internal.telegram;

public record InternalTelegramStatusResponse(
        boolean connected,
        String status
) {
}
