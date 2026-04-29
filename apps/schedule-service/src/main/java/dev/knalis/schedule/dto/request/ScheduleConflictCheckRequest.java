package dev.knalis.schedule.dto.request;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.WeekType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleConflictCheckRequest(
        UUID semesterId,
        UUID templateId,
        UUID overrideId,
        UUID subjectId,
        LocalDate date,
        DayOfWeek dayOfWeek,
        UUID groupId,
        UUID teacherId,
        UUID roomId,
        UUID slotId,
        WeekType weekType,
        LessonType lessonType,
        LessonFormat lessonFormat,
        OverrideType overrideType
) {
}
