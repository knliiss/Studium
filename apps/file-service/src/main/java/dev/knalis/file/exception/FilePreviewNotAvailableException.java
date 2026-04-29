package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FilePreviewNotAvailableException extends AppException {
    
    public FilePreviewNotAvailableException(UUID fileId) {
        super(
                HttpStatus.CONFLICT,
                "FILE_PREVIEW_NOT_AVAILABLE",
                "Preview is not available for the requested file",
                Map.of("fileId", fileId)
        );
    }
}
