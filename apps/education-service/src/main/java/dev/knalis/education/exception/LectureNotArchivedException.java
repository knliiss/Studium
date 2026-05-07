package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureNotArchivedException extends AppException {

    public LectureNotArchivedException(UUID lectureId) {
        super(
                HttpStatus.CONFLICT,
                "LECTURE_NOT_ARCHIVED",
                "Lecture must be archived first",
                Map.of("lectureId", lectureId)
        );
    }
}

