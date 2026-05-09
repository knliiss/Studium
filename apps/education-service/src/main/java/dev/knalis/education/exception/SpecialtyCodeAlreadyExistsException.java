package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class SpecialtyCodeAlreadyExistsException extends AppException {

    public SpecialtyCodeAlreadyExistsException(String code) {
        super(
                HttpStatus.CONFLICT,
                "SPECIALTY_CODE_ALREADY_EXISTS",
                "Specialty code already exists",
                Map.of("code", code)
        );
    }
}
