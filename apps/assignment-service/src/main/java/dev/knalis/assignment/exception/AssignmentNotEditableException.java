package dev.knalis.assignment.exception;

import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AssignmentNotEditableException extends AppException {

    public AssignmentNotEditableException(UUID assignmentId, AssignmentStatus status) {
        super(
                HttpStatus.CONFLICT,
                "ASSIGNMENT_NOT_EDITABLE",
                "Assignment cannot be edited in the current state",
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
