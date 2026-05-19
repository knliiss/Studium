package dev.knalis.gateway.client.auth;

import dev.knalis.gateway.client.auth.dto.AdminUserStatsResponse;
import dev.knalis.gateway.client.auth.dto.UserSummaryResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface AuthServiceClient {

    Mono<AdminUserStatsResponse> getAdminStats(String bearerToken, String requestId);

    Mono<List<UserSummaryResponse>> lookupUsers(String bearerToken, String requestId, List<UUID> userIds);
}
