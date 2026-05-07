package dev.knalis.education.exception;

import dev.knalis.education.entity.LectureStatus;
import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class LectureStateTransitionException extends AppException {

    public LectureStateTransitionException(UUID lectureId, LectureStatus currentStatus, LectureStatus targetStatus) {
        super(
                HttpStatus.CONFLICT,
                "LECTURE_STATE_TRANSITION_INVALID",
                "Lecture state transition is invalid",
                Map.of("lectureId", lectureId, "status", currentStatus, "targetStatus", targetStatus)
        );
    }
}

