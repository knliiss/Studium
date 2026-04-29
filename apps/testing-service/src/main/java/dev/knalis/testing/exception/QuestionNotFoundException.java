package dev.knalis.testing.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class QuestionNotFoundException extends AppException {
    
    public QuestionNotFoundException(UUID questionId) {
        super(
                HttpStatus.NOT_FOUND,
                "QUESTION_NOT_FOUND",
                "Question was not found",
                Map.of("questionId", questionId)
        );
    }
}
