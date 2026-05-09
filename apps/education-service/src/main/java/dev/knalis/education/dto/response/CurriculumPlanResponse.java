package dev.knalis.education.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CurriculumPlanResponse(
        UUID id,
        UUID specialtyId,
        Integer studyYear,
        Integer semesterNumber,
        UUID subjectId,
        Integer lectureCount,
        Integer practiceCount,
        Integer labCount,
        boolean supportsStreamLecture,
        boolean requiresSubgroupsForLabs,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
