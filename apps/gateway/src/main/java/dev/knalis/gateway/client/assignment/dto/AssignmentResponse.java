package dev.knalis.gateway.client.assignment.dto;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID topicId,
        String title,
        Instant deadline,
        String status,
        boolean allowLateSubmissions,
        int maxPoints
) {
}
