package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class SubmissionFileAccessDeniedException extends AppException {

    public SubmissionFileAccessDeniedException() {
        super(
                HttpStatus.FORBIDDEN,
                "SUBMISSION_FILE_ACCESS_DENIED",
                "Submission file access denied"
        );
    }
}
