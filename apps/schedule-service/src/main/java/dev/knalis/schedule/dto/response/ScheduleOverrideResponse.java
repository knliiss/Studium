package dev.knalis.schedule.dto.response;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleOverrideResponse(
        UUID id,
        UUID semesterId,
        UUID templateId,
        OverrideType overrideType,
        LocalDate date,
        UUID groupId,
        UUID subjectId,
        UUID teacherId,
        UUID slotId,
        LessonType lessonType,
        String lessonTypeDisplayName,
        LessonFormat lessonFormat,
        UUID roomId,
        String onlineMeetingUrl,
        String notes,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
