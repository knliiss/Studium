package dev.knalis.analytics.controller;

import dev.knalis.analytics.dto.response.SubjectAnalyticsPageResponse;
import dev.knalis.analytics.dto.response.SubjectAnalyticsResponse;
import dev.knalis.analytics.service.AnalyticsReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics/subjects")
@RequiredArgsConstructor
public class SubjectAnalyticsController {
    
    private final AnalyticsReadService analyticsReadService;
    
    @GetMapping("/{subjectId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SubjectAnalyticsPageResponse getSubjectAnalytics(
            @PathVariable UUID subjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return analyticsReadService.getSubjectAnalytics(subjectId, page, size, sortBy, direction);
    }
    
    @GetMapping("/{subjectId}/groups/{groupId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SubjectAnalyticsResponse getSubjectGroupAnalytics(
            @PathVariable UUID subjectId,
            @PathVariable UUID groupId
    ) {
        return analyticsReadService.getSubjectAnalytics(subjectId, groupId);
    }
}
