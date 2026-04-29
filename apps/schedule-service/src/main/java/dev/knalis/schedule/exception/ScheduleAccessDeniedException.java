package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ScheduleAccessDeniedException extends AppException {
    
    public ScheduleAccessDeniedException(String errorCode, String message, Map<String, Object> details) {
        super(HttpStatus.FORBIDDEN, errorCode, message, details);
    }
}
