package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import dev.knalis.testing.entity.TestStatus;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TestInvalidStateException extends AppException {

    public TestInvalidStateException(UUID testId, TestStatus status, String message) {
        super(
                HttpStatus.CONFLICT,
                "INVALID_TEST_STATE",
                message,
                details(testId, status)
        );
    }

    private static Map<String, Object> details(UUID testId, TestStatus status) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("testId", testId);
        details.put("status", status.name());
        return details;
    }
}
