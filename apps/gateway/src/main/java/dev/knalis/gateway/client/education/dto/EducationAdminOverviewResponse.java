package dev.knalis.gateway.client.education.dto;

public record EducationAdminOverviewResponse(
        long totalGroups,
        long totalSubjects,
        long totalTopics
) {
}
