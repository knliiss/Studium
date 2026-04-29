package dev.knalis.testing.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TestResultResponse(
        UUID id,
        UUID testId,
        UUID userId,
        UUID attemptId,
        int score,
        int autoScore,
        Integer manualOverrideScore,
        String manualOverrideReason,
        UUID reviewedByUserId,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
