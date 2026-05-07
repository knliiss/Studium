package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AssignmentAttachmentNotFoundException extends AppException {

    public AssignmentAttachmentNotFoundException(UUID attachmentId) {
        super(
                HttpStatus.NOT_FOUND,
                "ASSIGNMENT_ATTACHMENT_NOT_FOUND",
                "Assignment attachment was not found",
                Map.of("attachmentId", attachmentId)
        );
    }
}
