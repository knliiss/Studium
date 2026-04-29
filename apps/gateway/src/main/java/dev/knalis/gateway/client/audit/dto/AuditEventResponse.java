package dev.knalis.gateway.client.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String oldValueJson,
        String newValueJson,
        Instant occurredAt,
        String requestId,
        String sourceService
) {
}
