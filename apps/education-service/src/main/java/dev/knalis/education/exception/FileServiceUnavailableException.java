package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FileServiceUnavailableException extends AppException {

    public FileServiceUnavailableException(String operation, UUID fileId) {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                "FILE_SERVICE_UNAVAILABLE",
                "File service is temporarily unavailable",
                Map.of(
                        "operation", operation,
                        "fileId", fileId
                )
        );
    }
}
