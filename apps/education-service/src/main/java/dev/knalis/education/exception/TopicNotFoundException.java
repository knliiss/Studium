package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TopicNotFoundException extends AppException {
    
    public TopicNotFoundException(UUID topicId) {
        super(
                HttpStatus.NOT_FOUND,
                "TOPIC_NOT_FOUND",
                "Topic was not found",
                Map.of("topicId", topicId)
        );
    }
}
