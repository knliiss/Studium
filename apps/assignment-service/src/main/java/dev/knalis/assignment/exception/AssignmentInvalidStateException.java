package dev.knalis.assignment.exception;

import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssignmentInvalidStateException extends AppException {

    public AssignmentInvalidStateException(UUID assignmentId, AssignmentStatus status, String message) {
        super(
                HttpStatus.CONFLICT,
                "INVALID_ASSIGNMENT_STATE",
                message,
                details(assignmentId, status)
        );
    }

    private static Map<String, Object> details(UUID assignmentId, AssignmentStatus status) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("assignmentId", assignmentId);
        details.put("status", status.name());
        return details;
    }
}
