package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class ScheduleTemplateNotFoundException extends AppException {
    
    public ScheduleTemplateNotFoundException(UUID templateId) {
        super(
                HttpStatus.NOT_FOUND,
                "SCHEDULE_TEMPLATE_NOT_FOUND",
                "Schedule template was not found",
                Map.of("templateId", templateId)
        );
    }
}
