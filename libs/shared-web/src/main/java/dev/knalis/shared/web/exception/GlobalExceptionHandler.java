package dev.knalis.shared.web.exception;

import dev.knalis.shared.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(
            AppException ex,
            HttpServletRequest request
    ) {
        return respond(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request, ex.getDetails());
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid username or password",
                request,
                Map.of()
        );
    }
    
    @ExceptionHandler(InvalidBearerTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBearerToken(
            InvalidBearerTokenException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNAUTHORIZED,
                "INVALID_BEARER_TOKEN",
                "Bearer token is invalid or expired",
                request,
                Map.of()
        );
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access to the requested resource is denied",
                request,
                Map.of()
        );
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors
                    .computeIfAbsent(fieldError.getField(), key -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage());
        }
        
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fieldErrors", fieldErrors);
        details.put("errorCount", ex.getBindingResult().getErrorCount());
        details.put("objectName", ex.getBindingResult().getObjectName());
        
        return respond(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                request,
                details
        );
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parameter", ex.getName());
        details.put("rejectedValue", ex.getValue());
        details.put("expectedType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : null);
        
        return respond(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER_TYPE",
                "Invalid value for parameter '" + ex.getName() + "'",
                request,
                details
        );
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cause", ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        
        return respond(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST_BODY",
                "Request body is malformed or unreadable",
                request,
                details
        );
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("contentType", ex.getContentType() != null ? ex.getContentType().toString() : null);
        details.put("supportedMediaTypes", ex.getSupportedMediaTypes().stream().map(Object::toString).toList());
        
        return respond(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Content type is not supported",
                request,
                details
        );
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", ex.getMethod());
        details.put("supportedMethods", ex.getSupportedMethods() != null ? List.of(ex.getSupportedMethods()) : List.of());
        
        return respond(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this endpoint",
                request,
                details
        );
    }
    
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("resourcePath", ex.getResourcePath());
        
        return respond(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Requested resource was not found",
                request,
                details
        );
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "ILLEGAL_ARGUMENT",
                ex.getMessage(),
                request,
                Map.of()
        );
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("exception", ex.getClass().getSimpleName());
        
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal server error",
                request,
                details
        );
    }
    
    private ResponseEntity<ErrorResponse> respond(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        return ResponseEntity.status(status).body(build(status, errorCode, message, request, details));
    }
    
    private ErrorResponse build(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode,
                message,
                request.getRequestURI(),
                extractRequestId(request),
                details
        );
    }
    
    private String extractRequestId(HttpServletRequest request) {
        String headerRequestId = request.getHeader("X-Request-Id");
        if (headerRequestId != null && !headerRequestId.isBlank()) {
            return headerRequestId;
        }
        
        Object requestIdAttr = request.getAttribute("requestId");
        if (requestIdAttr instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        
        Object traceIdAttr = request.getAttribute("traceId");
        if (traceIdAttr instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        
        return null;
    }
}