package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SpecialtyNotFoundException extends AppException {

    public SpecialtyNotFoundException(UUID specialtyId) {
        super(
                HttpStatus.NOT_FOUND,
                "SPECIALTY_NOT_FOUND",
                "Specialty was not found",
                Map.of("specialtyId", specialtyId)
        );
    }
}
