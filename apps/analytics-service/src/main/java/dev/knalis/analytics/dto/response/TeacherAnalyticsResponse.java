package dev.knalis.analytics.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TeacherAnalyticsResponse(
        UUID teacherId,
        int publishedAssignmentsCount,
        int publishedTestsCount,
        int assignedGradesCount,
        Double averageReviewTimeHours,
        Double averageStudentScore,
        double failingRate,
        Instant updatedAt
) {
}
