package dev.knalis.shared.web.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public abstract class AppException extends RuntimeException {
    
    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> details;
    
    protected AppException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, Map.of());
    }
    
    protected AppException(HttpStatus status, String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }
    
    public HttpStatus getStatus() {
        return status;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
}