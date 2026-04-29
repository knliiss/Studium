package dev.knalis.gateway.client.testing.dto;

import java.util.List;

public record TeacherTestDashboardResponse(
        List<StudentTestDashboardItemResponse> activeTests
) {

    public static TeacherTestDashboardResponse empty() {
        return new TeacherTestDashboardResponse(List.of());
    }
}
