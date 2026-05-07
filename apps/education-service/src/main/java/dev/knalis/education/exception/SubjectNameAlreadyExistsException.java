package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SubjectNameAlreadyExistsException extends AppException {

    public SubjectNameAlreadyExistsException(String name) {
        super(
                HttpStatus.CONFLICT,
                "SUBJECT_NAME_ALREADY_EXISTS",
                "Subject name already exists",
                Map.of("name", name)
        );
    }
}
