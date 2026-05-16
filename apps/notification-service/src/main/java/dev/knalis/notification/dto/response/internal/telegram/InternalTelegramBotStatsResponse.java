package dev.knalis.notification.dto.response.internal.telegram;

public record InternalTelegramBotStatsResponse(
        long connectedUsersCount,
        long activeLinksCount,
        long disabledLinksCount,
        long deliveryFailuresCount,
        long telegramSentCount
) {
}
