package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FileAttachmentNotAllowedException extends AppException {

    public FileAttachmentNotAllowedException(UUID fileId) {
        super(
                HttpStatus.FORBIDDEN,
                "FILE_ATTACHMENT_NOT_ALLOWED",
                "File cannot be attached",
                Map.of("fileId", fileId)
        );
    }
}
