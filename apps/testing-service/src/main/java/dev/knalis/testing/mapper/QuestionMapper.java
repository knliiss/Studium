package dev.knalis.testing.mapper;

import dev.knalis.testing.dto.response.QuestionResponse;
import dev.knalis.testing.entity.Question;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface QuestionMapper {
    
    QuestionResponse toResponse(Question question);
}
