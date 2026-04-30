package dev.knalis.schedule.dto.request;

import dev.knalis.schedule.entity.LessonFormat;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.Subgroup;
import dev.knalis.schedule.entity.WeekType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.UUID;

public record CreateScheduleTemplateRequest(

        @NotNull
        UUID semesterId,

        @NotNull
        UUID groupId,

        @NotNull
        UUID subjectId,

        @NotNull
        UUID teacherId,

        @NotNull
        DayOfWeek dayOfWeek,

        @NotNull
        UUID slotId,

        @NotNull
        WeekType weekType,

        Subgroup subgroup,

        @NotNull
        LessonType lessonType,

        @NotNull
        LessonFormat lessonFormat,

        UUID roomId,

        @Size(max = 500)
        String onlineMeetingUrl,

        @Size(max = 2000)
        String notes,

        boolean active
) {
}
