package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GroupCurriculumOverrideResponse(
        UUID id,
        UUID groupId,
        UUID subjectId,
        boolean enabled,
        Integer lectureCountOverride,
        Integer practiceCountOverride,
        Integer labCountOverride,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
