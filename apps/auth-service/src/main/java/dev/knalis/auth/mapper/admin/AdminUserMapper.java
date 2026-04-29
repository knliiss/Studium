package dev.knalis.auth.mapper.admin;

import dev.knalis.auth.dto.response.AdminUserResponse;
import dev.knalis.auth.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminUserMapper {
    
    AdminUserResponse toResponse(User user);
}