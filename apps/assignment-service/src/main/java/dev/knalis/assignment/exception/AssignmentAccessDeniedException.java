package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssignmentAccessDeniedException extends AppException {

    public AssignmentAccessDeniedException(UUID assignmentId, UUID currentUserId) {
        super(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access to the assignment is denied",
                details(assignmentId, currentUserId)
        );
    }

    private static Map<String, Object> details(UUID assignmentId, UUID currentUserId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("assignmentId", assignmentId);
        details.put("currentUserId", currentUserId);
        return details;
    }
}
