package dev.knalis.education.mapper;

import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.entity.Group;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GroupMapper {
    
    GroupResponse toResponse(Group group);
}
