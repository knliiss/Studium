package dev.knalis.testing.mapper;

import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.entity.TestResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TestResultMapper {
    
    TestResultResponse toResponse(TestResult testResult);
}
