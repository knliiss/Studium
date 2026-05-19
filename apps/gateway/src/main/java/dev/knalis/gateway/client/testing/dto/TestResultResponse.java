package dev.knalis.gateway.client.testing.dto;

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
