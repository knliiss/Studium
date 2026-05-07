package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AssignmentNotAccessibleException extends AppException {

    public AssignmentNotAccessibleException(UUID assignmentId) {
        super(
                HttpStatus.FORBIDDEN,
                "ASSIGNMENT_NOT_ACCESSIBLE",
                "Assignment is not accessible",
                Map.of("assignmentId", assignmentId)
        );
    }
}
