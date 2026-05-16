package dev.knalis.notification.dto.response.internal.telegram;

import java.time.Instant;
import java.util.UUID;

public record InternalTelegramAdminUserItemResponse(
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
