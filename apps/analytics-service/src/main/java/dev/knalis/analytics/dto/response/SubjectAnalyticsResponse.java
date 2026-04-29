package dev.knalis.analytics.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SubjectAnalyticsResponse(
        UUID subjectId,
        UUID groupId,
        Double averageScore,
        double completionRate,
        double lateSubmissionRate,
        double missedDeadlineRate,
        long activeStudentsCount,
        long atRiskStudentsCount,
        int lectureOpenCount,
        int testCompletionCount,
        Instant updatedAt
) {
}
