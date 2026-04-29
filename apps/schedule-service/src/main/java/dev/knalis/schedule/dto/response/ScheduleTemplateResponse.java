package dev.knalis.schedule.dto.response;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.ScheduleTemplateStatus;
import dev.knalis.schedule.entity.WeekType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

public record ScheduleTemplateResponse(
        UUID id,
        UUID semesterId,
        UUID groupId,
        UUID subjectId,
        UUID teacherId,
        DayOfWeek dayOfWeek,
        UUID slotId,
        WeekType weekType,
        LessonType lessonType,
        String lessonTypeDisplayName,
        LessonFormat lessonFormat,
        UUID roomId,
        String onlineMeetingUrl,
        String notes,
        ScheduleTemplateStatus status,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
