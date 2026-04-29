package dev.knalis.gateway.client.audit;

import dev.knalis.gateway.client.audit.dto.AuditEventPageResponse;
import reactor.core.publisher.Mono;

public interface AuditServiceClient {

    Mono<AuditEventPageResponse> getAuditEvents(String bearerToken, String requestId, int page, int size, String sortBy, String direction);
}
