package dev.knalis.gateway.exception;

import org.springframework.http.HttpStatus;

public class GatewayClientException extends RuntimeException {
    
    private final HttpStatus status;
    private final String errorCode;
    
    public GatewayClientException(HttpStatus status, String message) {
        this(status, null, message);
    }

    public GatewayClientException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
    
    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
