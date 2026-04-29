package dev.knalis.assignment.client.audit;

import dev.knalis.assignment.client.audit.dto.CreateAuditEventRequest;

public interface AuditServiceClient {

    void createAuditEvent(CreateAuditEventRequest request);
}
