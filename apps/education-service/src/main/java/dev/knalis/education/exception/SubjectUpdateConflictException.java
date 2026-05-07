package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SubjectUpdateConflictException extends AppException {

    public SubjectUpdateConflictException(UUID subjectId, String reason) {
        super(
                HttpStatus.CONFLICT,
                "SUBJECT_UPDATE_CONFLICT",
                "Subject update conflict",
                details(subjectId, reason)
        );
    }

    private static Map<String, Object> details(UUID subjectId, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("subjectId", subjectId);
        details.put("reason", reason);
        return details;
    }
}
