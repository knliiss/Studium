package dev.knalis.education.controller;

import dev.knalis.education.dto.response.EducationAdminOverviewResponse;
import dev.knalis.education.service.dashboard.EducationDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/education/dashboard")
@RequiredArgsConstructor
public class EducationDashboardController {

    private final EducationDashboardService educationDashboardService;

    @GetMapping("/admin/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public EducationAdminOverviewResponse getAdminOverview() {
        return educationDashboardService.getAdminOverview();
    }
}
