package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class ScheduleOverrideNotFoundException extends AppException {
    
    public ScheduleOverrideNotFoundException(UUID overrideId) {
        super(
                HttpStatus.NOT_FOUND,
                "SCHEDULE_OVERRIDE_NOT_FOUND",
                "Schedule override was not found",
                Map.of("overrideId", overrideId)
        );
    }
}
