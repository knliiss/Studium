package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SpecialtyHasDependenciesException extends AppException {

    public SpecialtyHasDependenciesException(UUID specialtyId, String dependency) {
        super(
                HttpStatus.CONFLICT,
                "SPECIALTY_HAS_DEPENDENCIES",
                "Specialty has dependent entities",
                Map.of(
                        "specialtyId", specialtyId,
                        "dependency", dependency
                )
        );
    }
}
