package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class InvalidCurrentPasswordException extends AppException {
    
    public InvalidCurrentPasswordException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD", "Current password is invalid");
    }
}
