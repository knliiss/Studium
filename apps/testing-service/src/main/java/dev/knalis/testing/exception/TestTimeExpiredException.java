package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class TestTimeExpiredException extends AppException {

    public TestTimeExpiredException(UUID testId, UUID attemptId, Instant startedAt, int timeLimitMinutes) {
        super(
                HttpStatus.CONFLICT,
                "TEST_TIME_EXPIRED",
                "Test time limit has expired",
                Map.of(
                        "testId", testId,
                        "attemptId", attemptId,
                        "startedAt", startedAt,
                        "timeLimitMinutes", timeLimitMinutes
                )
        );
    }
}
