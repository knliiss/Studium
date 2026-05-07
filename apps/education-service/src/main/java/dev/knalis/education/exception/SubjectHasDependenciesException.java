package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SubjectHasDependenciesException extends AppException {

    public SubjectHasDependenciesException(UUID subjectId, String dependencyType) {
        super(
                HttpStatus.CONFLICT,
                "SUBJECT_HAS_DEPENDENCIES",
                "Subject binding cannot be changed because dependent entities exist",
                Map.of(
                        "subjectId", subjectId,
                        "dependencyType", dependencyType
                )
        );
    }
}
