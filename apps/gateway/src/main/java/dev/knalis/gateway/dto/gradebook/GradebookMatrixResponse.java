package dev.knalis.gateway.dto.gradebook;

import java.util.List;
import java.util.UUID;

public record GradebookMatrixResponse(
        UUID subjectId,
        String subjectName,
        UUID groupId,
        String groupName,
        List<String> teacherNames,
        String activityType,
        GradebookMatrixSummaryResponse summary,
        List<GradebookMatrixActivityResponse> activities,
        List<GradebookMatrixStudentResponse> students
) {
}
