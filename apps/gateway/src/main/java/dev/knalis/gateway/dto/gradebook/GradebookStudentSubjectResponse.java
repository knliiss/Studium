package dev.knalis.gateway.dto.gradebook;

import java.util.List;
import java.util.UUID;

public record GradebookStudentSubjectResponse(
        UUID subjectId,
        String subjectName,
        String groupName,
        List<String> teacherNames,
        Double averagePercent,
        Integer scoredPoints,
        Integer maxPoints,
        int assignmentsCompleted,
        int assignmentsTotal,
        int testsCompleted,
        int testsTotal,
        int pendingCount,
        int missingCount,
        List<GradebookActivityRowResponse> activities
) {
}
