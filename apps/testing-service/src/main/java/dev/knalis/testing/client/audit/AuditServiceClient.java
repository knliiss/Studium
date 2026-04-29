package dev.knalis.testing.client.audit;

import dev.knalis.testing.client.audit.dto.CreateAuditEventRequest;

public interface AuditServiceClient {

    void createAuditEvent(CreateAuditEventRequest request);
}
