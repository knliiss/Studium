package dev.knalis.gateway.dto.gradebook;

import java.time.Instant;
import java.util.UUID;

public record GradebookMatrixCellResponse(
        UUID activityId,
        Integer score,
        int maxPoints,
        Double percentage,
        String status,
        boolean late,
        Instant submittedAt,
        Instant reviewedAt,
        UUID submissionId,
        UUID resultId
) {
}
