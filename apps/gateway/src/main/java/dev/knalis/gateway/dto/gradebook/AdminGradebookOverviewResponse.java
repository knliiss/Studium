package dev.knalis.gateway.dto.gradebook;

import java.util.List;

public record AdminGradebookOverviewResponse(
        Double platformAveragePercent,
        int activeStudents,
        int subjectCount,
        int groupCount,
        int missingWorkCount,
        Double averageCompletionPercent,
        List<GradebookFilterOptionResponse> subjects,
        List<GradebookFilterOptionResponse> groups,
        List<GradebookFilterOptionResponse> teachers,
        List<GradebookAdminOverviewRowResponse> rows
) {
}
