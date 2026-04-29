package dev.knalis.auth.client.audit;

import dev.knalis.auth.client.audit.dto.CreateAuditEventRequest;

public interface AuditServiceClient {

    void createAuditEvent(CreateAuditEventRequest request);
}
