package dev.knalis.gateway.client.education;

import dev.knalis.gateway.client.education.dto.GroupMembershipResponse;
import dev.knalis.gateway.client.education.dto.GroupPageResponse;
import dev.knalis.gateway.client.education.dto.GroupResponse;
import dev.knalis.gateway.client.education.dto.GroupStudentMembershipResponse;
import dev.knalis.gateway.client.education.dto.EducationAdminOverviewResponse;
import dev.knalis.gateway.client.education.dto.SearchPageResponse;
import dev.knalis.gateway.client.education.dto.SubjectPageResponse;
import dev.knalis.gateway.client.education.dto.SubjectResponse;
import dev.knalis.gateway.client.education.dto.TopicResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface EducationServiceClient {
    
    Mono<List<GroupMembershipResponse>> getGroupsByUser(String bearerToken, String requestId, UUID userId);

    Mono<SearchPageResponse> search(String bearerToken, String requestId, String query, int page, int size);

    Mono<EducationAdminOverviewResponse> getAdminOverview(String bearerToken, String requestId);

    Mono<SubjectPageResponse> listSubjects(String bearerToken, String requestId, int size);

    Mono<GroupPageResponse> listGroups(String bearerToken, String requestId, int size);

    Mono<GroupResponse> getGroup(String bearerToken, String requestId, UUID groupId);

    Mono<List<GroupStudentMembershipResponse>> getGroupStudents(String bearerToken, String requestId, UUID groupId);

    Mono<List<TopicResponse>> getTopicsBySubject(String bearerToken, String requestId, UUID subjectId);
}
