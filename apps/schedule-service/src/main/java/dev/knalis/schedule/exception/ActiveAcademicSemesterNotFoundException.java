package dev.knalis.schedule.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

public class ActiveAcademicSemesterNotFoundException extends AppException {
    
    public ActiveAcademicSemesterNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "ACTIVE_ACADEMIC_SEMESTER_NOT_FOUND",
                "Active academic semester was not found"
        );
    }
}
