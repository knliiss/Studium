package dev.knalis.education.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCurriculumPlanRequest(
        @NotNull
        UUID specialtyId,

        @NotNull
        @Min(1)
        @Max(8)
        Integer studyYear,

        @NotNull
        @Min(1)
        @Max(2)
        Integer semesterNumber,

        @NotNull
        UUID subjectId,

        @NotNull
        @Min(0)
        Integer lectureCount,

        @NotNull
        @Min(0)
        Integer practiceCount,

        @NotNull
        @Min(0)
        Integer labCount,

        boolean supportsStreamLecture,
        boolean requiresSubgroupsForLabs
) {
}
