package dev.knalis.assignment.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class FileAccessDeniedException extends AppException {

    public FileAccessDeniedException() {
        super(
                HttpStatus.FORBIDDEN,
                "FILE_ACCESS_DENIED",
                "File access denied"
        );
    }
}
