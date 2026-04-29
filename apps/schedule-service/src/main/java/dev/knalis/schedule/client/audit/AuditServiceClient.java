package dev.knalis.schedule.client.audit;

import dev.knalis.schedule.client.audit.dto.CreateAuditEventRequest;

public interface AuditServiceClient {

    void createAuditEvent(CreateAuditEventRequest request);
}
