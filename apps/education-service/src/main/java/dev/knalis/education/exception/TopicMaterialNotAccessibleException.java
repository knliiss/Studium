package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TopicMaterialNotAccessibleException extends AppException {

    public TopicMaterialNotAccessibleException(UUID materialId) {
        super(
                HttpStatus.FORBIDDEN,
                "MATERIAL_NOT_ACCESSIBLE",
                "Topic material is not accessible",
                Map.of("materialId", materialId)
        );
    }
}

