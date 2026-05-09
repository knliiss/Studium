package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class CurriculumPlanNotFoundException extends AppException {

    public CurriculumPlanNotFoundException(UUID curriculumPlanId) {
        super(
                HttpStatus.NOT_FOUND,
                "CURRICULUM_PLAN_NOT_FOUND",
                "Curriculum plan was not found",
                Map.of("curriculumPlanId", curriculumPlanId)
        );
    }
}
