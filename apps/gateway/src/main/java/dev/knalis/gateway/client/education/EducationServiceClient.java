package dev.knalis.gateway.client.education;

import dev.knalis.gateway.client.education.dto.GroupMembershipResponse;
import dev.knalis.gateway.client.education.dto.EducationAdminOverviewResponse;
import dev.knalis.gateway.client.education.dto.SearchPageResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface EducationServiceClient {
    
    Mono<List<GroupMembershipResponse>> getGroupsByUser(String bearerToken, String requestId, UUID userId);

    Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size);

    Mono<EducationAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId);
}
