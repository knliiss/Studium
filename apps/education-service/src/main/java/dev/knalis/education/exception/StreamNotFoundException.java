package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class StreamNotFoundException extends AppException {

    public StreamNotFoundException(UUID streamId) {
        super(
                HttpStatus.NOT_FOUND,
                "STREAM_NOT_FOUND",
                "Stream was not found",
                Map.of("streamId", streamId)
        );
    }
}
