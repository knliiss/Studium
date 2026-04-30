package dev.knalis.schedule.dto.response;

import dev.knalis.schedule.entity.Subgroup;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleConflictItemResponse(
        String type,
        String message,
        UUID conflictingEntityId,
        String conflictingEntityType,
        LocalDate date,
        DayOfWeek dayOfWeek,
        UUID slotId,
        UUID groupId,
        Subgroup subgroup,
        UUID teacherId,
        UUID roomId
) {
}
