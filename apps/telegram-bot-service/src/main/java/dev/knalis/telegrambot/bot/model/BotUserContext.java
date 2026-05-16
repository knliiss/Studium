package dev.knalis.telegrambot.bot.model;

import java.util.UUID;

public record BotUserContext(
        long chatId,
        long telegramUserId,
        String telegramUsername,
        String firstName,
        String lastName,
        String languageCode,
        boolean linked,
        UUID userId,
        boolean telegramEnabled,
        boolean notifyAssignments,
        boolean notifyTests,
        boolean notifyGrades,
        boolean notifySchedule,
        boolean notifyMaterials,
        boolean notifySystem,
        BotUserRole role,
        BotLocale locale
) {
}
