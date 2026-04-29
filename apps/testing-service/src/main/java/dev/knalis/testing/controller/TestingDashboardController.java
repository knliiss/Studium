package dev.knalis.testing.controller;

import dev.knalis.shared.security.user.CurrentUserService;
import dev.knalis.testing.dto.response.StudentTestDashboardResponse;
import dev.knalis.testing.dto.response.TeacherTestDashboardResponse;
import dev.knalis.testing.dto.response.TestingAdminOverviewResponse;
import dev.knalis.testing.service.dashboard.TestingDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/testing/dashboard")
@RequiredArgsConstructor
public class TestingDashboardController {

    private final TestingDashboardService testingDashboardService;
    private final CurrentUserService currentUserService;

    @GetMapping("/student/me")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public StudentTestDashboardResponse getStudentDashboard(Authentication authentication) {
        return testingDashboardService.getStudentDashboard(currentUserService.getCurrentUserId(authentication));
    }

    @GetMapping("/teacher/me")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TeacherTestDashboardResponse getTeacherDashboard(Authentication authentication) {
        return testingDashboardService.getTeacherDashboard(currentUserService.getCurrentUserId(authentication));
    }

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public TestingAdminOverviewResponse getAdminOverview() {
        return testingDashboardService.getAdminOverview();
    }
}
