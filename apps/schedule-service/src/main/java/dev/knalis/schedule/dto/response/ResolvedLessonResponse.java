package dev.knalis.schedule.dto.response;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.ResolvedLessonSourceType;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.entity.WeekType;

import java.time.LocalDate;
import java.util.UUID;

public record ResolvedLessonResponse(
        LocalDate date,
        UUID semesterId,
        UUID templateId,
        UUID groupId,
        UUID subjectId,
        UUID teacherId,
        UUID slotId,
        Subgroup subgroup,
        int weekNumber,
        WeekType weekType,
        LessonType lessonType,
        String lessonTypeDisplayName,
        LessonFormat lessonFormat,
        UUID roomId,
        String onlineMeetingUrl,
        String notes,
        ResolvedLessonSourceType sourceType,
        OverrideType overrideType
) {
}
