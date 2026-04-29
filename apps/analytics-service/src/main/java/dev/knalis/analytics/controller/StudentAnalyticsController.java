package dev.knalis.analytics.controller;

import dev.knalis.analytics.dto.response.StudentAnalyticsResponse;
import dev.knalis.analytics.dto.response.StudentRiskResponse;
import dev.knalis.analytics.dto.response.SubjectAnalyticsResponse;
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
@RequestMapping("/api/v1/analytics/students")
@RequiredArgsConstructor
public class StudentAnalyticsController {
    
    private final AnalyticsReadService analyticsReadService;
    private final CurrentUserService currentUserService;
    
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public StudentAnalyticsResponse getStudentAnalytics(Authentication authentication, @PathVariable UUID userId) {
        assertSelfOrAdmin(authentication, userId);
        return analyticsReadService.getStudentAnalytics(userId);
    }
    
    @GetMapping("/{userId}/subjects")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public List<SubjectAnalyticsResponse> getStudentSubjects(Authentication authentication, @PathVariable UUID userId) {
        assertSelfOrAdmin(authentication, userId);
        return analyticsReadService.getStudentSubjects(userId);
    }
    
    @GetMapping("/{userId}/risk")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public StudentRiskResponse getStudentRisk(Authentication authentication, @PathVariable UUID userId) {
        assertSelfOrAdmin(authentication, userId);
        return analyticsReadService.getStudentRisk(userId);
    }
    
    private void assertSelfOrAdmin(Authentication authentication, UUID userId) {
        boolean admin = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> "ROLE_OWNER".equals(authority) || "ROLE_ADMIN".equals(authority));
        if (!admin && !currentUserService.getCurrentUserId(authentication).equals(userId)) {
            throw new AnalyticsAccessDeniedException(userId);
        }
    }
}
