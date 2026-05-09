package dev.knalis.schedule.exception;

import dev.knalis.schedule.entity.LessonType;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class RoomCapabilityInvalidPriorityException extends AppException {

    public RoomCapabilityInvalidPriorityException(UUID roomId, LessonType lessonType, Integer priority) {
        super(
                HttpStatus.BAD_REQUEST,
                "ROOM_CAPABILITY_INVALID_PRIORITY",
                "Room capability priority must be positive",
                Map.of(
                        "roomId", roomId,
                        "lessonType", lessonType,
                        "priority", priority
                )
        );
    }
}
