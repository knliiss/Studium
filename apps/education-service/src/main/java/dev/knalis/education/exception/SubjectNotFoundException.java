package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubjectNotFoundException extends AppException {
    
    public SubjectNotFoundException(UUID subjectId) {
        super(
                HttpStatus.NOT_FOUND,
                "SUBJECT_NOT_FOUND",
                "Subject was not found",
                Map.of("subjectId", subjectId)
        );
    }
}
