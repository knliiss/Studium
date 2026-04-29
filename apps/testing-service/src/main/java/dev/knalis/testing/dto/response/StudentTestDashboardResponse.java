package dev.knalis.testing.dto.response;

import java.util.List;

public record StudentTestDashboardResponse(
        List<StudentTestDashboardItemResponse> upcomingDeadlines,
        List<StudentTestDashboardItemResponse> availableTests
) {
}
