package dev.knalis.schedule.mapper;

import dev.knalis.schedule.dto.response.LessonSlotResponse;
import dev.knalis.schedule.entity.LessonSlot;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LessonSlotMapper {
    
    LessonSlotResponse toResponse(LessonSlot lessonSlot);
}
