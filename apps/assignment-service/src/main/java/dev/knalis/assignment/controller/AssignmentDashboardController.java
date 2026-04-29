package dev.knalis.assignment.controller;

import dev.knalis.assignment.dto.response.AssignmentAdminOverviewResponse;
import dev.knalis.assignment.dto.response.StudentAssignmentDashboardResponse;
import dev.knalis.assignment.dto.response.TeacherAssignmentDashboardResponse;
import dev.knalis.assignment.service.dashboard.AssignmentDashboardService;
import dev.knalis.shared.security.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignments/dashboard")
@RequiredArgsConstructor
public class AssignmentDashboardController {

    private final AssignmentDashboardService assignmentDashboardService;
    private final CurrentUserService currentUserService;

    @GetMapping("/student/me")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public StudentAssignmentDashboardResponse getStudentDashboard(Authentication authentication) {
        return assignmentDashboardService.getStudentDashboard(currentUserService.getCurrentUserId(authentication));
    }

    @GetMapping("/teacher/me")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TeacherAssignmentDashboardResponse getTeacherDashboard(Authentication authentication) {
        return assignmentDashboardService.getTeacherDashboard(currentUserService.getCurrentUserId(authentication));
    }

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public AssignmentAdminOverviewResponse getAdminOverview() {
        return assignmentDashboardService.getAdminOverview();
    }
}
