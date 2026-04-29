package dev.knalis.analytics.controller;

import dev.knalis.analytics.dto.response.GroupOverviewResponse;
import dev.knalis.analytics.dto.response.StudentGroupProgressPageResponse;
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
@RequestMapping("/api/v1/analytics/groups")
@RequiredArgsConstructor
public class GroupAnalyticsController {
    
    private final AnalyticsReadService analyticsReadService;
    
    @GetMapping("/{groupId}/overview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public GroupOverviewResponse getGroupOverview(@PathVariable UUID groupId) {
        return analyticsReadService.getGroupOverview(groupId);
    }
    
    @GetMapping("/{groupId}/students")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public StudentGroupProgressPageResponse getGroupStudents(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return analyticsReadService.getGroupStudents(groupId, page, size, sortBy, direction);
    }
}
