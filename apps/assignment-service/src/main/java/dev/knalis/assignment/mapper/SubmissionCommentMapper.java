package dev.knalis.assignment.mapper;

import dev.knalis.assignment.dto.response.SubmissionCommentResponse;
import dev.knalis.assignment.entity.SubmissionComment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubmissionCommentMapper {

    @Mapping(target = "body", expression = "java(submissionComment.isDeleted() ? null : submissionComment.getBody())")
    SubmissionCommentResponse toResponse(SubmissionComment submissionComment);
}
