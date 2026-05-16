package dev.knalis.notification.dto.response.internal.telegram;

import java.util.List;

public record InternalTelegramAdminUsersResponse(
        List<InternalTelegramAdminUserItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
