package dev.knalis.gateway.dto.gradebook;

import java.util.List;
import java.util.UUID;

public record AdminGradebookResponse(
        List<GradebookFilterOptionResponse> subjects,
        List<GradebookFilterOptionResponse> groups,
        List<GradebookFilterOptionResponse> teachers,
        UUID selectedSubjectId,
        UUID selectedGroupId,
        UUID selectedTeacherId,
        List<GradebookAdminOverviewRowResponse> rows,
        GradebookMatrixResponse matrix
) {
}
