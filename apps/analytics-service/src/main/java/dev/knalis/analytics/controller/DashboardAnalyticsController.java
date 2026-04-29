package dev.knalis.analytics.controller;

import dev.knalis.analytics.dto.response.DashboardOverviewResponse;
import dev.knalis.analytics.service.AnalyticsReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/dashboard")
@RequiredArgsConstructor
public class DashboardAnalyticsController {
    
    private final AnalyticsReadService analyticsReadService;
    
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public DashboardOverviewResponse getDashboardOverview() {
        return analyticsReadService.getDashboardOverview();
    }
}
