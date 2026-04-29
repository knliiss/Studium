package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ScheduleValidationException extends AppException {
    
    public ScheduleValidationException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
    
    public ScheduleValidationException(String errorCode, String message, Map<String, Object> details) {
        super(HttpStatus.BAD_REQUEST, errorCode, message, details);
    }
}
