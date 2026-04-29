package dev.knalis.schedule.mapper;

import dev.knalis.schedule.dto.response.ScheduleOverrideResponse;
import dev.knalis.schedule.entity.ScheduleOverride;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduleOverrideMapper {
    
    @Mapping(
            target = "lessonTypeDisplayName",
            expression = "java(scheduleOverride.getLessonType() == null ? null : scheduleOverride.getLessonType().getDisplayName())"
    )
    ScheduleOverrideResponse toResponse(ScheduleOverride scheduleOverride);
}
