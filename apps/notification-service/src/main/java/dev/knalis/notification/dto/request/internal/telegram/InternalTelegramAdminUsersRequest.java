package dev.knalis.notification.dto.request.internal.telegram;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InternalTelegramAdminUsersRequest(
        @Min(0) int page,
        @Min(1) @Max(20) int size
) {
}
