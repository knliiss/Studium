package dev.knalis.testing.mapper;

import dev.knalis.testing.dto.response.AnswerResponse;
import dev.knalis.testing.entity.Answer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AnswerMapper {
    
    @Mapping(target = "isCorrect", source = "correct")
    AnswerResponse toResponse(Answer answer);
}
