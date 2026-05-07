package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AssignmentClosedException extends AppException {

    public AssignmentClosedException(UUID assignmentId) {
        super(
                HttpStatus.CONFLICT,
                "ASSIGNMENT_CLOSED",
                "Assignment is closed for new submissions",
                Map.of("assignmentId", assignmentId)
        );
    }
}
