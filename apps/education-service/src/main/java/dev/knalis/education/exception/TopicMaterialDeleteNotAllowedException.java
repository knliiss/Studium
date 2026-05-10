package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TopicMaterialDeleteNotAllowedException extends AppException {

    public TopicMaterialDeleteNotAllowedException(UUID materialId) {
        super(
                HttpStatus.CONFLICT,
                "MATERIAL_DELETE_NOT_ALLOWED",
                "Archive material before deleting",
                Map.of("materialId", materialId)
        );
    }
}

