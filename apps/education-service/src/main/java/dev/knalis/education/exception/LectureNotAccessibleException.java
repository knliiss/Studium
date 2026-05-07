package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureNotAccessibleException extends AppException {

    public LectureNotAccessibleException(UUID lectureId) {
        super(
                HttpStatus.FORBIDDEN,
                "LECTURE_NOT_ACCESSIBLE",
                "Lecture is not accessible",
                Map.of("lectureId", lectureId)
        );
    }
}

