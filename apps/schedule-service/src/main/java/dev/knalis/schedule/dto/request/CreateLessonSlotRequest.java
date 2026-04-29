package dev.knalis.schedule.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CreateLessonSlotRequest(
        
        @NotNull
        @Min(1)
        Integer number,
        
        @NotNull
        LocalTime startTime,
        
        @NotNull
        LocalTime endTime,
        
        boolean active
) {
}
