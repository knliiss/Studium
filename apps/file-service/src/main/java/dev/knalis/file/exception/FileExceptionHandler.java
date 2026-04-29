package dev.knalis.file.exception;

import dev.knalis.shared.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class FileExceptionHandler {
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMultipartLimit(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        FileUploadLimitExceededException appException = new FileUploadLimitExceededException();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                new ErrorResponse(
                        Instant.now(),
                        HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                        appException.getErrorCode(),
                        appException.getMessage(),
                        request.getRequestURI(),
                        request.getHeader("X-Request-Id"),
                        Map.of()
                )
        );
    }
}
