package dev.knalis.assignment.mapper;

import dev.knalis.assignment.dto.response.SubmissionResponse;
import dev.knalis.assignment.entity.Submission;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubmissionMapper {
    
    SubmissionResponse toResponse(Submission submission);
}
