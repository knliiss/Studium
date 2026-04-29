package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AssignmentNotFoundException extends AppException {
    
    public AssignmentNotFoundException(UUID assignmentId) {
        super(
                HttpStatus.NOT_FOUND,
                "ASSIGNMENT_NOT_FOUND",
                "Assignment was not found",
                Map.of("assignmentId", assignmentId)
        );
    }
}
