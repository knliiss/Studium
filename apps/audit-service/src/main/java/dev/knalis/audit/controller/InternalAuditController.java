package dev.knalis.audit.controller;

import dev.knalis.audit.dto.request.CreateAuditEventRequest;
import dev.knalis.audit.dto.response.AuditEventResponse;
import dev.knalis.audit.service.AuditEventService;
import dev.knalis.audit.service.InternalRequestGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit")
@RequiredArgsConstructor
public class InternalAuditController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final AuditEventService auditEventService;
    private final InternalRequestGuard internalRequestGuard;

    @PostMapping("/events")
    public AuditEventResponse createAuditEvent(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody CreateAuditEventRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return auditEventService.createAuditEvent(request);
    }
}
