package dev.knalis.notification.dto.response.telegram;

public record TelegramPreferencesResponse(
        boolean telegramEnabled,
        boolean notifyAssignments,
        boolean notifyTests,
        boolean notifyGrades,
        boolean notifySchedule,
        boolean notifyMaterials,
        boolean notifySystem
) {
}
