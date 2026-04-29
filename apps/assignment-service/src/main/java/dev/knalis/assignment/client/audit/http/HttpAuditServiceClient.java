package dev.knalis.assignment.client.audit.http;

import dev.knalis.assignment.client.audit.AuditServiceClient;
import dev.knalis.assignment.client.audit.dto.CreateAuditEventRequest;
import dev.knalis.assignment.config.AssignmentAuditServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class HttpAuditServiceClient implements AuditServiceClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient auditServiceRestClient;
    private final AssignmentAuditServiceProperties properties;

    @Override
    public void createAuditEvent(CreateAuditEventRequest request) {
        auditServiceRestClient.post()
                .uri("/internal/audit/events")
                .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
