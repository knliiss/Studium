package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubmissionAttemptNotCurrentException extends AppException {

    public SubmissionAttemptNotCurrentException(UUID submissionId) {
        super(
                HttpStatus.CONFLICT,
                "SUBMISSION_ATTEMPT_NOT_CURRENT",
                "Only the current submission attempt can be reviewed or resubmitted",
                Map.of("submissionId", submissionId)
        );
    }
}
