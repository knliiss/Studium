package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class TopicSubjectMismatchException extends AppException {

    public TopicSubjectMismatchException(UUID subjectId, UUID topicId) {
        super(
                HttpStatus.CONFLICT,
                "TOPIC_SUBJECT_MISMATCH",
                "Topic does not belong to the subject",
                Map.of("subjectId", subjectId, "topicId", topicId)
        );
    }
}

