package dev.knalis.file.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class EmptyFileUploadException extends AppException {
    
    public EmptyFileUploadException() {
        super(HttpStatus.BAD_REQUEST, "EMPTY_FILE_UPLOAD", "Uploaded file is empty");
    }
}
