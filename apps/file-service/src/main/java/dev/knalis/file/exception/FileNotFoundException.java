package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class FileNotFoundException extends AppException {
    
    public FileNotFoundException(UUID fileId) {
        super(
                HttpStatus.NOT_FOUND,
                "FILE_NOT_FOUND",
                "Requested file was not found",
                Map.of("fileId", fileId)
        );
    }
}
