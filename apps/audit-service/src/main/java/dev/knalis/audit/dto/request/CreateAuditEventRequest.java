package dev.knalis.audit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateAuditEventRequest(
        UUID id,
        @NotNull UUID actorUserId,
        @NotBlank String action,
        @NotBlank String entityType,
        @NotNull UUID entityId,
        String oldValueJson,
        String newValueJson,
        Instant occurredAt,
        String requestId,
        @NotBlank String sourceService
) {
}
