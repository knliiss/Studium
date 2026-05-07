package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureNotFoundException extends AppException {

    public LectureNotFoundException(UUID lectureId) {
        super(
                HttpStatus.NOT_FOUND,
                "LECTURE_NOT_FOUND",
                "Lecture was not found",
                Map.of("lectureId", lectureId)
        );
    }
}

