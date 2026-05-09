package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class CurriculumPlanAlreadyExistsException extends AppException {

    public CurriculumPlanAlreadyExistsException(UUID specialtyId, Integer studyYear, Integer semesterNumber, UUID subjectId) {
        super(
                HttpStatus.CONFLICT,
                "CURRICULUM_PLAN_ALREADY_EXISTS",
                "Curriculum plan already exists",
                Map.of(
                        "specialtyId", specialtyId,
                        "studyYear", studyYear,
                        "semesterNumber", semesterNumber,
                        "subjectId", subjectId
                )
        );
    }
}
