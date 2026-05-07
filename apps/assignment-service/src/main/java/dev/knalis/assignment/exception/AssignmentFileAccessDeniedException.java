package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class AssignmentFileAccessDeniedException extends AppException {

    public AssignmentFileAccessDeniedException() {
        super(
                HttpStatus.FORBIDDEN,
                "ASSIGNMENT_FILE_ACCESS_DENIED",
                "Assignment file access denied"
        );
    }
}
