package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TestHasAttemptsException extends AppException {

    public TestHasAttemptsException(UUID testId, long attemptsCount, long resultsCount) {
        super(
                HttpStatus.CONFLICT,
                "TEST_HAS_ATTEMPTS",
                "Test has attempts or results and cannot be permanently deleted",
                details(testId, attemptsCount, resultsCount)
        );
    }

    private static Map<String, Object> details(UUID testId, long attemptsCount, long resultsCount) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("testId", testId);
        details.put("attemptsCount", attemptsCount);
        details.put("resultsCount", resultsCount);
        return details;
    }
}
