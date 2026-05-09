package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class RoomCapabilityNotFoundException extends AppException {

    public RoomCapabilityNotFoundException(UUID roomCapabilityId) {
        super(
                HttpStatus.NOT_FOUND,
                "ROOM_CAPABILITY_NOT_FOUND",
                "Room capability was not found",
                Map.of("roomCapabilityId", roomCapabilityId)
        );
    }
}
