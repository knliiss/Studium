package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TestAccessDeniedException extends AppException {

    public TestAccessDeniedException(UUID testId, UUID currentUserId) {
        super(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access to the test is denied",
                details(testId, currentUserId)
        );
    }

    private static Map<String, Object> details(UUID testId, UUID currentUserId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("testId", testId);
        details.put("currentUserId", currentUserId);
        return details;
    }
}
