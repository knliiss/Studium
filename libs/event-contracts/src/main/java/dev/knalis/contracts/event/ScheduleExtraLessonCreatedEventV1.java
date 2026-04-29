package dev.knalis.contracts.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleExtraLessonCreatedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID semesterId,
        UUID overrideId,
        LocalDate date,
        UUID groupId,
        UUID subjectId,
        UUID teacherId,
        UUID slotId,
        ScheduleLessonTypeV1 lessonType,
        ScheduleLessonFormatV1 lessonFormat,
        UUID roomId,
        String onlineMeetingUrl,
        String notes,
        UUID createdByUserId
) {
}
