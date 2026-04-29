package dev.knalis.gateway.dto;

import java.time.Instant;
import java.util.UUID;

public record DashboardAuditEventResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        Instant occurredAt,
        String sourceService
) {
}
