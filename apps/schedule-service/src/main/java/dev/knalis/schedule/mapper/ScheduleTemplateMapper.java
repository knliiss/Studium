package dev.knalis.schedule.mapper;

import dev.knalis.schedule.dto.response.ScheduleTemplateResponse;
import dev.knalis.schedule.entity.ScheduleTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduleTemplateMapper {
    
    @Mapping(
            target = "lessonTypeDisplayName",
            expression = "java(scheduleTemplate.getLessonType() == null ? null : scheduleTemplate.getLessonType().getDisplayName())"
    )
    ScheduleTemplateResponse toResponse(ScheduleTemplate scheduleTemplate);
}
