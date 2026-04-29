package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class FileStorageException extends AppException {
    
    public FileStorageException(String operation, String message, Throwable cause) {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "FILE_STORAGE_ERROR",
                message,
                Map.of("operation", operation)
        );
        initCause(cause);
    }
}
