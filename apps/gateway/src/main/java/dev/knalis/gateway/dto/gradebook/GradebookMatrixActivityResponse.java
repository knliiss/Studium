package dev.knalis.gateway.dto.gradebook;

import java.time.Instant;
import java.util.UUID;

public record GradebookMatrixActivityResponse(
        UUID activityId,
        String activityType,
        String title,
        int maxPoints,
        Instant dueAt
) {
}
