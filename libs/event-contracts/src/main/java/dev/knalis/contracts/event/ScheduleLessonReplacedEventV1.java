package dev.knalis.contracts.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScheduleLessonReplacedEventV1(
        UUID eventId,
        Instant occurredAt,
        UUID semesterId,
        UUID overrideId,
        LocalDate date,
        UUID oldTeacherId,
        UUID newTeacherId,
        UUID oldRoomId,
        UUID newRoomId,
        UUID oldSlotId,
        UUID newSlotId,
        ScheduleLessonTypeV1 lessonType,
        ScheduleLessonFormatV1 oldLessonFormat,
        ScheduleLessonFormatV1 newLessonFormat,
        String onlineMeetingUrl,
        UUID groupId,
        UUID subjectId,
        UUID changedByUserId
) {
}
