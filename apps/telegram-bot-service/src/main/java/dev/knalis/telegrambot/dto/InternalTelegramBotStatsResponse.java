package dev.knalis.telegrambot.dto;

public record InternalTelegramBotStatsResponse(
        long connectedUsersCount,
        long activeLinksCount,
        long disabledLinksCount,
        long deliveryFailuresCount,
        long telegramSentCount
) {
}
