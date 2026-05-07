package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AssignmentAlreadyArchivedException extends AppException {

    public AssignmentAlreadyArchivedException(UUID assignmentId) {
        super(
                HttpStatus.CONFLICT,
                "ASSIGNMENT_ALREADY_ARCHIVED",
                "Assignment is already archived",
                Map.of("assignmentId", assignmentId)
        );
    }
}
