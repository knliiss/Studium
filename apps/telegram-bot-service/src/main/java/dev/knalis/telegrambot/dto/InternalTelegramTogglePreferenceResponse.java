package dev.knalis.telegrambot.dto;

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
