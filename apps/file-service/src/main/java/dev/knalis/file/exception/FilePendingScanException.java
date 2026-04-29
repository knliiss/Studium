package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FilePendingScanException extends AppException {
    
    public FilePendingScanException(UUID fileId) {
        super(
                HttpStatus.CONFLICT,
                "FILE_PENDING_SCAN",
                "Requested file is pending malware scan",
                Map.of("fileId", fileId)
        );
    }
}
