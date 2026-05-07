package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubmissionNotAccessibleException extends AppException {

    public SubmissionNotAccessibleException(UUID submissionId) {
        super(
                HttpStatus.FORBIDDEN,
                "SUBMISSION_NOT_ACCESSIBLE",
                "Submission is not accessible",
                Map.of("submissionId", submissionId)
        );
    }
}
