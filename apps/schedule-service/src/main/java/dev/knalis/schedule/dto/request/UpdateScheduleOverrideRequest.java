package dev.knalis.schedule.dto.request;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.OverrideType;
import dev.knalis.schedule.entity.Subgroup;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateScheduleOverrideRequest(

        UUID semesterId,

        UUID templateId,

        @NotNull
        OverrideType overrideType,

        @NotNull
        LocalDate date,

        UUID groupId,

        UUID subjectId,

        UUID teacherId,

        UUID slotId,

        Subgroup subgroup,

        LessonType lessonType,

        LessonFormat lessonFormat,

        UUID roomId,

        @Size(max = 500)
        String onlineMeetingUrl,

        @Size(max = 2000)
        String notes
) {
}
