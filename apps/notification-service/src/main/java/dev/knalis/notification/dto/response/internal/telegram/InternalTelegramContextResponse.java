package dev.knalis.notification.dto.response.internal.telegram;

import java.util.UUID;

public record InternalTelegramContextResponse(
        boolean linked,
        UUID userId,
        boolean active,
        boolean telegramEnabled,
        boolean notifyAssignments,
        boolean notifyTests,
        boolean notifyGrades,
        boolean notifySchedule,
        boolean notifyMaterials,
        boolean notifySystem,
        String locale
) {
}
