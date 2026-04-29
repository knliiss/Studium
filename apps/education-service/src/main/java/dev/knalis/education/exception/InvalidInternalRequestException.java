package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class InvalidInternalRequestException extends AppException {
    
    public InvalidInternalRequestException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "INVALID_INTERNAL_REQUEST",
                "Internal request authentication failed",
                Map.of()
        );
    }
}
