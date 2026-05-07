package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureAlreadyArchivedException extends AppException {

    public LectureAlreadyArchivedException(UUID lectureId) {
        super(
                HttpStatus.CONFLICT,
                "LECTURE_ALREADY_ARCHIVED",
                "Lecture is already archived",
                Map.of("lectureId", lectureId)
        );
    }
}

