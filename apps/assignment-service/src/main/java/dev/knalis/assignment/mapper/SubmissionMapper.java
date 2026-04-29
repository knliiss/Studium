package dev.knalis.assignment.mapper;

import dev.knalis.assignment.dto.response.SubmissionResponse;
import dev.knalis.assignment.entity.Submission;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubmissionMapper {
    
    SubmissionResponse toResponse(Submission submission);
}
