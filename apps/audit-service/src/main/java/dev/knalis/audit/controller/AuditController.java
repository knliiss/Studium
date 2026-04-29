package dev.knalis.audit.controller;

import dev.knalis.audit.dto.response.AuditEventPageResponse;
import dev.knalis.audit.dto.response.AuditEventResponse;
import dev.knalis.audit.service.AuditEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventService auditEventService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public AuditEventPageResponse getAuditEvents(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "occurredAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return auditEventService.getAuditEvents(
                actorId,
                entityType,
                entityId,
                dateFrom,
                dateTo,
                page,
                size,
                (sortBy == null || sortBy.isBlank()) ? sort : sortBy,
                direction
        );
    }

    @GetMapping("/{auditEventId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public AuditEventResponse getAuditEvent(@PathVariable UUID auditEventId) {
        return auditEventService.getAuditEvent(auditEventId);
    }
}
