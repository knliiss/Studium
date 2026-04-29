package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class InvalidSubmissionFileException extends AppException {
    
    public InvalidSubmissionFileException(UUID fileId, String message) {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_SUBMISSION_FILE",
                message,
                Map.of("fileId", fileId)
        );
    }
}
