package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FileRejectedException extends AppException {
    
    public FileRejectedException(UUID fileId) {
        super(
                HttpStatus.CONFLICT,
                "FILE_REJECTED",
                "Requested file was rejected by the scan pipeline",
                Map.of("fileId", fileId)
        );
    }
}
