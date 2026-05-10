package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class TopicMaterialValidationException extends AppException {

    public TopicMaterialValidationException(String field, String message) {
        super(
                HttpStatus.BAD_REQUEST,
                "MATERIAL_VALIDATION_FAILED",
                message,
                Map.of("field", field)
        );
    }
}

