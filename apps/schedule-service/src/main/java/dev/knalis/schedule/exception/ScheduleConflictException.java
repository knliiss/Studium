package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ScheduleConflictException extends AppException {
    
    public ScheduleConflictException(String errorCode, String message, Map<String, Object> details) {
        super(HttpStatus.CONFLICT, errorCode, message, details);
    }
}
