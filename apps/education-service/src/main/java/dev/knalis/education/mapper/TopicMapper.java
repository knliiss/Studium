package dev.knalis.education.mapper;

import dev.knalis.education.dto.response.TopicResponse;
import dev.knalis.education.entity.Topic;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TopicMapper {
    
    TopicResponse toResponse(Topic topic);
}
