package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class SubjectBindingRequiredException extends AppException {

    public SubjectBindingRequiredException() {
        super(
                HttpStatus.BAD_REQUEST,
                "SUBJECT_GROUP_BINDING_REQUIRED",
                "Subject must be assigned to at least one group"
        );
    }
}
