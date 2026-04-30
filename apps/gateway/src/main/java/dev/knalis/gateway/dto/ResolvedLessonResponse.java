package dev.knalis.gateway.dto;

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
        String subgroup,
        int weekNumber,
        String weekType,
        String lessonType,
        String lessonTypeDisplayName,
        String lessonFormat,
        UUID roomId,
        String onlineMeetingUrl,
        String notes,
        String sourceType,
        String overrideType
) {
}
