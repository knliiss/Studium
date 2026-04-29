package dev.knalis.testing.mapper;

import dev.knalis.testing.dto.response.TestResponse;
import dev.knalis.testing.entity.Test;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TestMapper {
    
    TestResponse toResponse(Test test);
}
