package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class StreamHasGroupsException extends AppException {

    public StreamHasGroupsException(UUID streamId) {
        super(
                HttpStatus.CONFLICT,
                "STREAM_HAS_GROUPS",
                "Stream has assigned groups",
                Map.of("streamId", streamId)
        );
    }
}
