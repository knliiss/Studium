package dev.knalis.education.client.audit;

import dev.knalis.education.client.audit.dto.CreateAuditEventRequest;

public interface AuditServiceClient {

    void createAuditEvent(CreateAuditEventRequest request);
}
