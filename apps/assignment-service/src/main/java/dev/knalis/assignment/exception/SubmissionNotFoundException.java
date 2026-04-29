package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubmissionNotFoundException extends AppException {
    
    public SubmissionNotFoundException(UUID submissionId) {
        super(
                HttpStatus.NOT_FOUND,
                "SUBMISSION_NOT_FOUND",
                "Submission was not found",
                Map.of("submissionId", submissionId)
        );
    }
}
