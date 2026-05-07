package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TestNotArchivedException extends AppException {

    public TestNotArchivedException(UUID testId) {
        super(
                HttpStatus.CONFLICT,
                "CONTENT_NOT_ARCHIVED",
                "Test is not archived",
                Map.of("testId", testId)
        );
    }
}
