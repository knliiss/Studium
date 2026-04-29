package dev.knalis.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardDeadlineItemResponse(
        String type,
        UUID id,
        UUID topicId,
        UUID subjectId,
        String title,
        Instant deadline
) {
}
