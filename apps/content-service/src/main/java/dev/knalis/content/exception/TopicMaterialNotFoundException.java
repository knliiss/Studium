package dev.knalis.content.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TopicMaterialNotFoundException extends AppException {

    public TopicMaterialNotFoundException(UUID topicMaterialId) {
        super(
                HttpStatus.NOT_FOUND,
                "TOPIC_MATERIAL_NOT_FOUND",
                "Topic material was not found",
                Map.of("topicMaterialId", topicMaterialId)
        );
    }
}

