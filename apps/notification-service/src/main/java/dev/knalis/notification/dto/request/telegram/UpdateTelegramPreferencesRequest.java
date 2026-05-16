package dev.knalis.notification.dto.request.telegram;

public record UpdateTelegramPreferencesRequest(
        Boolean telegramEnabled,
        Boolean notifyAssignments,
        Boolean notifyTests,
        Boolean notifyGrades,
        Boolean notifySchedule,
        Boolean notifyMaterials,
        Boolean notifySystem
) {
}
