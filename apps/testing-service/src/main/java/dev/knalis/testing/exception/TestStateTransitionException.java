package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import dev.knalis.testing.entity.TestStatus;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TestStateTransitionException extends AppException {

    public TestStateTransitionException(UUID testId, TestStatus fromStatus, TestStatus targetStatus) {
        super(
                HttpStatus.CONFLICT,
                "INVALID_STATE_TRANSITION",
                "Test state transition is not allowed",
                details(testId, fromStatus, targetStatus)
        );
    }

    private static Map<String, Object> details(UUID testId, TestStatus fromStatus, TestStatus targetStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("testId", testId);
        details.put("fromStatus", fromStatus.name());
        details.put("targetStatus", targetStatus.name());
        return details;
    }
}
