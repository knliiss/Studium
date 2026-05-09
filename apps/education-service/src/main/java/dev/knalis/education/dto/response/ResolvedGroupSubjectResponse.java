package dev.knalis.education.dto.response;

import java.util.List;
import java.util.UUID;

public record ResolvedGroupSubjectResponse(
        UUID subjectId,
        String subjectName,
        ResolvedGroupSubjectSource source,
        Integer lectureCount,
        Integer practiceCount,
        Integer labCount,
        List<UUID> teacherIds,
        boolean supportsStreamLecture,
        boolean requiresSubgroupsForLabs,
        boolean disabledByOverride
) {
}
