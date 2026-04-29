package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class MaxAttemptsExceededException extends AppException {

    public MaxAttemptsExceededException(UUID testId, UUID userId, int maxAttempts) {
        super(
                HttpStatus.CONFLICT,
                "MAX_ATTEMPTS_EXCEEDED",
                "Maximum number of attempts for this test has been reached",
                Map.of(
                        "testId", testId,
                        "userId", userId,
                        "maxAttempts", maxAttempts
                )
        );
    }
}
