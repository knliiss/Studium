package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubmissionAttachmentNotFoundException extends AppException {

    public SubmissionAttachmentNotFoundException(UUID attachmentId) {
        super(
                HttpStatus.NOT_FOUND,
                "SUBMISSION_ATTACHMENT_NOT_FOUND",
                "Submission attachment was not found",
                Map.of("attachmentId", attachmentId)
        );
    }
}
