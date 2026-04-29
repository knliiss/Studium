package dev.knalis.profile.mapper;

import dev.knalis.profile.dto.response.UserProfileResponse;
import dev.knalis.profile.entity.UserProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {
    
    UserProfileResponse toResponse(UserProfile profile);
}