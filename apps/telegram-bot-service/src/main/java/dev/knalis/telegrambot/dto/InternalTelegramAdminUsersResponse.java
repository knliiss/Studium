package dev.knalis.telegrambot.dto;

import java.util.List;

public record InternalTelegramAdminUsersResponse(
        List<InternalTelegramBotUserItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
