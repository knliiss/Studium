package dev.knalis.education.exception;

import dev.knalis.education.entity.LectureStatus;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureNotEditableException extends AppException {

    public LectureNotEditableException(UUID lectureId, LectureStatus status) {
        super(
                HttpStatus.CONFLICT,
                "LECTURE_NOT_EDITABLE",
                "Lecture is not editable in the current state",
                Map.of("lectureId", lectureId, "status", status)
        );
    }
}

