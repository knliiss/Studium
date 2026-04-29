package dev.knalis.auth.mapper.auth;

import dev.knalis.auth.dto.response.UserAuthResponse;
import dev.knalis.auth.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAuthMapper {
    
    UserAuthResponse toResponse(User user);
}