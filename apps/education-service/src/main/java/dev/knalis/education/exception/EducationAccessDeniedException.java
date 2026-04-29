package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class EducationAccessDeniedException extends AppException {

    public EducationAccessDeniedException() {
        super(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access denied"
        );
    }
}
