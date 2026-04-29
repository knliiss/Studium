package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TestNotAvailableException extends AppException {

    public TestNotAvailableException(UUID testId, Instant availableFrom, Instant availableUntil) {
        super(
                HttpStatus.CONFLICT,
                "TEST_NOT_AVAILABLE",
                "Test is not available at the current time",
                details(testId, availableFrom, availableUntil)
        );
    }

    private static Map<String, Object> details(UUID testId, Instant availableFrom, Instant availableUntil) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("testId", testId);
        details.put("availableFrom", availableFrom);
        details.put("availableUntil", availableUntil);
        return details;
    }
}
