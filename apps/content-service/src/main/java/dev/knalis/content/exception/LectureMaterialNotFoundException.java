package dev.knalis.content.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureMaterialNotFoundException extends AppException {

    public LectureMaterialNotFoundException(UUID lectureMaterialId) {
        super(
                HttpStatus.NOT_FOUND,
                "LECTURE_MATERIAL_NOT_FOUND",
                "Lecture material was not found",
                Map.of("lectureMaterialId", lectureMaterialId)
        );
    }
}

