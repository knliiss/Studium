package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AssignmentNotArchivedException extends AppException {

    public AssignmentNotArchivedException(UUID assignmentId) {
        super(
                HttpStatus.CONFLICT,
                "ASSIGNMENT_NOT_ARCHIVED",
                "Assignment is not archived",
                Map.of("assignmentId", assignmentId)
        );
    }
}
