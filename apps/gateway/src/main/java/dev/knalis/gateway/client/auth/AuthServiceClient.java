package dev.knalis.gateway.client.auth;

import dev.knalis.gateway.client.auth.dto.AdminUserStatsResponse;
import reactor.core.publisher.Mono;

public interface AuthServiceClient {

    Mono<AdminUserStatsResponse> getAdminStats(String bearerToken, String requestId);
}
