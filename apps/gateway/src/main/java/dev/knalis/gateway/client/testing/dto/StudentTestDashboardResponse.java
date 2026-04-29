package dev.knalis.gateway.client.testing.dto;

import java.util.List;

public record StudentTestDashboardResponse(
        List<StudentTestDashboardItemResponse> upcomingDeadlines,
        List<StudentTestDashboardItemResponse> availableTests
) {
    public static StudentTestDashboardResponse empty() {
        return new StudentTestDashboardResponse(List.of(), List.of());
    }
}
