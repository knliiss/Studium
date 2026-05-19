package dev.knalis.gateway.dto.gradebook;

import java.util.List;
import java.util.UUID;

public record GradebookAdminOverviewRowResponse(
        UUID subjectId,
        String subjectName,
        UUID groupId,
        String groupName,
        List<UUID> teacherIds,
        List<String> teacherNames,
        int studentCount,
        Double averagePercent,
        int highCount,
        int goodCount,
        int averageCount,
        int lowCount,
        int missingCount,
        Double completionPercent
) {
}
