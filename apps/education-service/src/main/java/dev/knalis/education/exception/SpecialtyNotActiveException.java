package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class SpecialtyNotActiveException extends AppException {

    public SpecialtyNotActiveException(UUID specialtyId) {
        super(
                HttpStatus.CONFLICT,
                "SPECIALTY_NOT_ACTIVE",
                "Specialty is archived and cannot be used",
                Map.of("specialtyId", specialtyId)
        );
    }
}
