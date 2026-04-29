package dev.knalis.analytics.controller;

import dev.knalis.analytics.dto.response.GroupOverviewResponse;
import dev.knalis.analytics.dto.response.TeacherAnalyticsResponse;
import dev.knalis.analytics.exception.AnalyticsAccessDeniedException;
import dev.knalis.analytics.service.AnalyticsReadService;
import dev.knalis.shared.security.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics/teachers")
@RequiredArgsConstructor
public class TeacherAnalyticsController {
    
    private final AnalyticsReadService analyticsReadService;
    private final CurrentUserService currentUserService;
    
    @GetMapping("/{teacherId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TeacherAnalyticsResponse getTeacherAnalytics(Authentication authentication, @PathVariable UUID teacherId) {
        if (!isAdmin(authentication) && !currentUserService.getCurrentUserId(authentication).equals(teacherId)) {
            throw new AnalyticsAccessDeniedException(teacherId);
        }
        return analyticsReadService.getTeacherAnalytics(teacherId);
    }

    @GetMapping("/{teacherId}/groups-at-risk")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<GroupOverviewResponse> getTeacherGroupsAtRisk(
            Authentication authentication,
            @PathVariable UUID teacherId
    ) {
        if (!isAdmin(authentication) && !currentUserService.getCurrentUserId(authentication).equals(teacherId)) {
            throw new AnalyticsAccessDeniedException(teacherId);
        }
        return analyticsReadService.getTeacherGroupsAtRisk(teacherId);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> "ROLE_OWNER".equals(authority) || "ROLE_ADMIN".equals(authority));
    }
}
