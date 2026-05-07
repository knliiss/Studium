package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureHasDependenciesException extends AppException {

    public LectureHasDependenciesException(UUID lectureId, String reason) {
        super(
                HttpStatus.CONFLICT,
                "LECTURE_HAS_DEPENDENCIES",
                "Lecture has dependencies and cannot be deleted",
                Map.of("lectureId", lectureId, "reason", reason)
        );
    }
}

