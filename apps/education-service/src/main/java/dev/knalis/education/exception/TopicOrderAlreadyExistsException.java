package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TopicOrderAlreadyExistsException extends AppException {
    
    public TopicOrderAlreadyExistsException(UUID subjectId, int orderIndex) {
        super(
                HttpStatus.CONFLICT,
                "TOPIC_ORDER_ALREADY_EXISTS",
                "Topic order index already exists for this subject",
                Map.of(
                        "subjectId", subjectId,
                        "orderIndex", orderIndex
                )
        );
    }
}
