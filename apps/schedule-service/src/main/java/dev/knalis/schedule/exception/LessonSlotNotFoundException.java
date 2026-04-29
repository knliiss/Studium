package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LessonSlotNotFoundException extends AppException {
    
    public LessonSlotNotFoundException(UUID slotId) {
        super(
                HttpStatus.NOT_FOUND,
                "LESSON_SLOT_NOT_FOUND",
                "Lesson slot was not found",
                Map.of("slotId", slotId)
        );
    }
}
