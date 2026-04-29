package dev.knalis.education.dto.response;

public record EducationAdminOverviewResponse(
        long totalGroups,
        long totalSubjects,
        long totalTopics
) {
}
