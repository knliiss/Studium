package dev.knalis.education.client.audit.http;

import dev.knalis.education.client.audit.AuditServiceClient;
import dev.knalis.education.client.audit.dto.CreateAuditEventRequest;
import dev.knalis.education.config.EducationAuditServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class HttpAuditServiceClient implements AuditServiceClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient auditServiceRestClient;
    private final EducationAuditServiceProperties properties;

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
