package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TopicMaterialNotFoundException extends AppException {

    public TopicMaterialNotFoundException(UUID materialId) {
        super(
                HttpStatus.NOT_FOUND,
                "MATERIAL_NOT_FOUND",
                "Topic material was not found",
                Map.of("materialId", materialId)
        );
    }
}

