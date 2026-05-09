package dev.knalis.schedule.dto.request;

import dev.knalis.schedule.entity.LessonType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpsertRoomCapabilityRequest(
        @NotNull
        LessonType lessonType,

        @NotNull
        @Min(1)
        Integer priority,

        boolean active
) {
}
