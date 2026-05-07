package dev.knalis.content.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class ContentAccessDeniedException extends AppException {

    public ContentAccessDeniedException(UUID topicId, UUID userId) {
        super(
                HttpStatus.FORBIDDEN,
                "CONTENT_ACCESS_DENIED",
                "User does not have access to manage content in this topic",
                Map.of("topicId", topicId, "userId", userId)
        );
    }
}

