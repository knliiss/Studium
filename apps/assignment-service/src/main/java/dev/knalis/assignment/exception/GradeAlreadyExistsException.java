package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class GradeAlreadyExistsException extends AppException {
    
    public GradeAlreadyExistsException(UUID submissionId) {
        super(
                HttpStatus.CONFLICT,
                "GRADE_ALREADY_EXISTS",
                "Grade already exists for this submission",
                Map.of("submissionId", submissionId)
        );
    }
}
