package dev.knalis.education.mapper;

import dev.knalis.education.dto.response.TopicMaterialResponse;
import dev.knalis.education.entity.TopicMaterial;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TopicMaterialMapper {

    TopicMaterialResponse toResponse(TopicMaterial material);
}

