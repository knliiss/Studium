package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class FileUploadLimitExceededException extends AppException {
    
    public FileUploadLimitExceededException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "MULTIPART_LIMIT_EXCEEDED", "Multipart request exceeds configured upload limits");
    }
}
