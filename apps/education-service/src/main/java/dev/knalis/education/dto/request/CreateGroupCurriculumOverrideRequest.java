package dev.knalis.education.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateGroupCurriculumOverrideRequest(
        @NotNull
        UUID subjectId,

        boolean enabled,

        @Min(0)
        Integer lectureCountOverride,

        @Min(0)
        Integer practiceCountOverride,

        @Min(0)
        Integer labCountOverride,

        @Size(max = 1000)
        String notes
) {
}
