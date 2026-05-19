package dev.knalis.gateway.dto.gradebook;

import java.util.List;
import java.util.UUID;

public record TeacherGradebookResponse(
        List<GradebookFilterOptionResponse> subjects,
        List<GradebookFilterOptionResponse> groups,
        UUID selectedSubjectId,
        UUID selectedGroupId,
        String activityType,
        GradebookMatrixResponse matrix
) {
}
