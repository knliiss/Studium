package dev.knalis.file.exception;

import dev.knalis.file.entity.StoredFileStatus;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class InvalidFileLifecycleTransitionException extends AppException {
    
    public InvalidFileLifecycleTransitionException(UUID fileId, StoredFileStatus currentStatus, String targetState) {
        super(
                HttpStatus.CONFLICT,
                "INVALID_FILE_LIFECYCLE_TRANSITION",
                "File lifecycle transition is not allowed",
                Map.of(
                        "fileId", fileId,
                        "currentStatus", currentStatus,
                        "targetState", targetState
                )
        );
    }
}
