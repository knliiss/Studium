package dev.knalis.telegrambot.dto;

import java.time.Instant;
import java.util.UUID;

public record InternalTelegramNotificationItem(
        UUID id,
        String title,
        String body,
        String type,
        String category,
        Instant createdAt
) {
}
