package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class StreamNotActiveException extends AppException {

    public StreamNotActiveException(UUID streamId) {
        super(
                HttpStatus.CONFLICT,
                "STREAM_NOT_ACTIVE",
                "Stream is archived and cannot be used",
                Map.of("streamId", streamId)
        );
    }
}
