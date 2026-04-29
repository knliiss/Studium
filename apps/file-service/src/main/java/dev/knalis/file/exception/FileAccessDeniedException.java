package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FileAccessDeniedException extends AppException {
    
    public FileAccessDeniedException(UUID fileId) {
        super(
                HttpStatus.FORBIDDEN,
                "FILE_ACCESS_DENIED",
                "Access to the requested file is denied",
                Map.of("fileId", fileId)
        );
    }
}
