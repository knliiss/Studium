package dev.knalis.notification.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class WebSocketAuthenticationException extends AppException {
    
    public WebSocketAuthenticationException(String message) {
        super(
                HttpStatus.UNAUTHORIZED,
                "WEBSOCKET_AUTHENTICATION_FAILED",
                message,
                Map.of()
        );
    }
}
