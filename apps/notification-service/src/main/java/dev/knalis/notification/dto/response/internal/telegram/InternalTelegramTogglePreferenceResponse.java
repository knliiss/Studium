package dev.knalis.notification.dto.response.internal.telegram;

public record InternalTelegramTogglePreferenceResponse(
        boolean telegramEnabled,
        boolean notifyAssignments,
        boolean notifyTests,
        boolean notifyGrades,
        boolean notifySchedule,
        boolean notifyMaterials,
        boolean notifySystem
) {
}
