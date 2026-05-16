package dev.knalis.telegrambot.dto;

import java.time.Instant;
import java.util.UUID;

public record InternalTelegramBotUserItem(
        UUID linkId,
        UUID userId,
        String studiumUsername,
        String telegramUsername,
        Long telegramUserId,
        Instant connectedAt,
        boolean active,
        Instant disconnectedAt,
        String lastDeliveryFailure
) {
}
