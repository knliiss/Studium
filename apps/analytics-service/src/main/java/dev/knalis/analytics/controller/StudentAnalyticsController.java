package dev.knalis.analytics.controller;

import dev.knalis.analytics.dto.response.StudentAnalyticsResponse;
import dev.knalis.analytics.dto.response.StudentRiskResponse;
import dev.knalis.analytics.dto.response.SubjectAnalyticsResponse;
import dev.knalis.analytics.exception.AnalyticsAccessDeniedException;
import dev.knalis.analytics.service.AnalyticsAccessService;
import dev.knalis.analytics.service.AnalyticsReadService;
import dev.knalis.shared.security.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/analytics/students")
@RequiredArgsConstructor
public class StudentAnalyticsController {
    
    private final AnalyticsReadService analyticsReadService;
    private final AnalyticsAccessService analyticsAccessService;
    private final CurrentUserService currentUserService;
    
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public StudentAnalyticsResponse getStudentAnalytics(Authentication authentication, @PathVariable UUID userId) {
        assertSelfOrAdmin(authentication, userId);
        return analyticsReadService.getStudentAnalytics(userId);
    }
    
    @GetMapping("/{userId}/subjects")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<SubjectAnalyticsResponse> getStudentSubjects(Authentication authentication, @PathVariable UUID userId) {
        assertSelfOrAdmin(authentication, userId);
        return analyticsReadService.getStudentSubjects(userId);
    }
    
    @GetMapping("/{userId}/risk")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public StudentRiskResponse getStudentRisk(Authentication authentication, @PathVariable UUID userId) {
        assertSelfOrAdmin(authentication, userId);
        return analyticsReadService.getStudentRisk(userId);
    }
    
    private void assertSelfOrAdmin(Authentication authentication, UUID userId) {
        UUID currentUserId = currentUserService.getCurrentUserId(authentication);
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (roles.contains("ROLE_OWNER") || roles.contains("ROLE_ADMIN")) {
            return;
        }
        if (currentUserId.equals(userId)) {
            return;
        }
        if (roles.contains("ROLE_TEACHER") && analyticsAccessService.canTeacherAccessStudent(currentUserId, userId)) {
            return;
        }
        if (roles.contains("ROLE_STUDENT")) {
            throw new AnalyticsAccessDeniedException(userId);
        }
        throw new AnalyticsAccessDeniedException(userId);
    }
}
