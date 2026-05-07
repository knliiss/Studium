package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class InvalidSubjectBindingException extends AppException {

    public InvalidSubjectBindingException(String bindingType, UUID subjectId, UUID entityId) {
        super(
                HttpStatus.BAD_REQUEST,
                "INVALID_SUBJECT_BINDING",
                "Subject binding request is invalid",
                details(bindingType, subjectId, entityId)
        );
    }

    private static Map<String, Object> details(String bindingType, UUID subjectId, UUID entityId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bindingType", bindingType);
        details.put("subjectId", subjectId);
        details.put("entityId", entityId);
        return details;
    }
}
