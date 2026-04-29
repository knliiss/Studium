package dev.knalis.assignment.mapper;

import dev.knalis.assignment.dto.response.AssignmentResponse;
import dev.knalis.assignment.entity.Assignment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AssignmentMapper {
    
    AssignmentResponse toResponse(Assignment assignment);
}
