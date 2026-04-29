package dev.knalis.testing.dto.response;

import java.util.List;

public record TeacherTestDashboardResponse(
        List<StudentTestDashboardItemResponse> activeTests
) {

    public static TeacherTestDashboardResponse empty() {
        return new TeacherTestDashboardResponse(List.of());
    }
}
