package dev.knalis.gateway.exception;

import dev.knalis.gateway.filter.RequestIdFilter;
import dev.knalis.shared.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GatewayExceptionHandler {
    
    @ExceptionHandler(GatewayClientException.class)
    public ResponseEntity<ErrorResponse> handleGatewayClientException(
            GatewayClientException exception,
            ServerWebExchange exchange
    ) {
        return respond(
                exchange,
                exception.getStatus(),
                exception.getErrorCode() == null ? defaultErrorCode(exception.getStatus()) : exception.getErrorCode(),
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDateRange(
            InvalidDateRangeException exception,
            ServerWebExchange exchange
    ) {
        return respond(
                exchange,
                HttpStatus.BAD_REQUEST,
                "INVALID_DATE_RANGE",
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleWebInput(
            ServerWebInputException exception,
            ServerWebExchange exchange
    ) {
        return respond(
                exchange,
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException exception,
            ServerWebExchange exchange
    ) {
        return respond(
                exchange,
                HttpStatus.BAD_REQUEST,
                "ILLEGAL_ARGUMENT",
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            ServerWebExchange exchange
    ) {
        return respond(
                exchange,
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access to the requested resource is denied",
                Map.of()
        );
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            ServerWebExchange exchange
    ) {
        return respond(
                exchange,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal server error",
                Map.of()
        );
    }
    
    private ResponseEntity<ErrorResponse> respond(
            ServerWebExchange exchange,
            HttpStatus status,
            String errorCode,
            String message,
            Map<String, Object> details
    ) {
        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        }

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode,
                message,
                exchange.getRequest().getURI().getPath(),
                requestId,
                details
        );

        return ResponseEntity.status(status).body(response);
    }

    private String defaultErrorCode(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return "UNAUTHORIZED";
        }
        if (status == HttpStatus.FORBIDDEN) {
            return "ACCESS_DENIED";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "ENTITY_NOT_FOUND";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "VALIDATION_FAILED";
        }
        return status.is5xxServerError() ? "DOWNSTREAM_ERROR" : "GATEWAY_ERROR";
    }
}
