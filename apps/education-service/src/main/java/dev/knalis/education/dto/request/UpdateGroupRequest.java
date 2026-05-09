package dev.knalis.education.dto.request;

import dev.knalis.education.entity.GroupSubgroupMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateGroupRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        UUID specialtyId,

        @Min(1)
        @Max(8)
        Integer studyYear,

        UUID streamId,

        GroupSubgroupMode subgroupMode
) {
}
