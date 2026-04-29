package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class AcademicSemesterNotFoundException extends AppException {
    
    public AcademicSemesterNotFoundException(UUID semesterId) {
        super(
                HttpStatus.NOT_FOUND,
                "ACADEMIC_SEMESTER_NOT_FOUND",
                "Academic semester was not found",
                Map.of("semesterId", semesterId)
        );
    }
}
