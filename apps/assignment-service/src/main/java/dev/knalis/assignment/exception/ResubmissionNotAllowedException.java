package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class ResubmissionNotAllowedException extends AppException {

    public ResubmissionNotAllowedException(UUID assignmentId) {
        super(
                HttpStatus.CONFLICT,
                "RESUBMISSION_NOT_ALLOWED",
                "Submission cannot be modified for this assignment",
                Map.of("assignmentId", assignmentId)
        );
    }
}
