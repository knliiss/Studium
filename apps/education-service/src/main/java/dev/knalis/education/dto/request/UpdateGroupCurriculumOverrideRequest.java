package dev.knalis.education.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateGroupCurriculumOverrideRequest(
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
