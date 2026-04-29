package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class FileTooLargeException extends AppException {
    
    public FileTooLargeException(long actualSizeBytes, long maxSizeBytes) {
        super(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "Uploaded file exceeds the allowed size limit",
                Map.of(
                        "actualSizeBytes", actualSizeBytes,
                        "maxSizeBytes", maxSizeBytes
                )
        );
    }
}
