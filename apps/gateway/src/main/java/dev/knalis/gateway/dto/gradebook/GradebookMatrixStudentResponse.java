package dev.knalis.gateway.dto.gradebook;

import java.util.List;
import java.util.UUID;

public record GradebookMatrixStudentResponse(
        UUID studentId,
        String studentName,
        Double averagePercent,
        int missingCount,
        List<GradebookMatrixCellResponse> cells
) {
}
