package dev.knalis.gateway.dto.gradebook;

import java.util.List;

public record StudentGradebookResponse(
        Double averagePercent,
        int gradedActivities,
        int pendingActivities,
        int missingActivities,
        List<GradebookStudentSubjectResponse> subjects
) {
}
