package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class ActiveTestAttemptNotFoundException extends AppException {

    public ActiveTestAttemptNotFoundException(UUID testId, UUID userId) {
        super(
                HttpStatus.CONFLICT,
                "ACTIVE_TEST_ATTEMPT_NOT_FOUND",
                "Active test attempt was not found",
                Map.of(
                        "testId", testId,
                        "userId", userId
                )
        );
    }
}
