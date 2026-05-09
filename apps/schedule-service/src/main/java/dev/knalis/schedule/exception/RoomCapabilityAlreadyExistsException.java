package dev.knalis.schedule.exception;

import dev.knalis.schedule.entity.LessonType;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class RoomCapabilityAlreadyExistsException extends AppException {

    public RoomCapabilityAlreadyExistsException(UUID roomId, LessonType lessonType) {
        super(
                HttpStatus.CONFLICT,
                "ROOM_CAPABILITY_ALREADY_EXISTS",
                "Room capability already exists",
                Map.of(
                        "roomId", roomId,
                        "lessonType", lessonType
                )
        );
    }
}
