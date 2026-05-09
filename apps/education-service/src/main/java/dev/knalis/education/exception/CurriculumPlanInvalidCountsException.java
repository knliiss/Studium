package dev.knalis.education.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class CurriculumPlanInvalidCountsException extends AppException {

    public CurriculumPlanInvalidCountsException(Integer lectureCount, Integer practiceCount, Integer labCount) {
        super(
                HttpStatus.BAD_REQUEST,
                "CURRICULUM_PLAN_INVALID_COUNTS",
                "Curriculum plan lesson counts are invalid",
                Map.of(
                        "lectureCount", lectureCount,
                        "practiceCount", practiceCount,
                        "labCount", labCount
                )
        );
    }
}
