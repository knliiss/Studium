package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class RoomNotFoundException extends AppException {
    
    public RoomNotFoundException(UUID roomId) {
        super(
                HttpStatus.NOT_FOUND,
                "ROOM_NOT_FOUND",
                "Room was not found",
                Map.of("roomId", roomId)
        );
    }
}
