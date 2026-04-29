package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubmissionCommentNotFoundException extends AppException {

    public SubmissionCommentNotFoundException(UUID commentId) {
        super(
                HttpStatus.NOT_FOUND,
                "SUBMISSION_COMMENT_NOT_FOUND",
                "Submission comment was not found",
                Map.of("commentId", commentId)
        );
    }
}
