package dev.knalis.schedule.mapper;

import dev.knalis.schedule.dto.response.AcademicSemesterResponse;
import dev.knalis.schedule.entity.AcademicSemester;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AcademicSemesterMapper {
    
    AcademicSemesterResponse toResponse(AcademicSemester academicSemester);
}
