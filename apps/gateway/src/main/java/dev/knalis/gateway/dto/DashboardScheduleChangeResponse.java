package dev.knalis.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardScheduleChangeResponse(
        UUID notificationId,
        String type,
        String title,
        String body,
        Instant createdAt
) {
}
