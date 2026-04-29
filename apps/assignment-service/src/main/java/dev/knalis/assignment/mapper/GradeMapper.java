package dev.knalis.assignment.mapper;

import dev.knalis.assignment.dto.response.GradeResponse;
import dev.knalis.assignment.entity.Grade;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GradeMapper {
    
    GradeResponse toResponse(Grade grade);
}
