package dev.knalis.auth.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class AccessHierarchyViolationException extends AppException {
    
    public AccessHierarchyViolationException(String message) {
        super(
                HttpStatus.FORBIDDEN,
                "ACCESS_HIERARCHY_VIOLATION",
                message
        );
    }
}