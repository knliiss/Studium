package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TestNotFoundException extends AppException {
    
    public TestNotFoundException(UUID testId) {
        super(
                HttpStatus.NOT_FOUND,
                "TEST_NOT_FOUND",
                "Test was not found",
                Map.of("testId", testId)
        );
    }
}
