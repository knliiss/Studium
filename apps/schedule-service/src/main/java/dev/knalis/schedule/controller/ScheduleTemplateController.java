package dev.knalis.schedule.controller;

import dev.knalis.schedule.dto.request.BulkCreateScheduleTemplatesRequest;
import dev.knalis.schedule.dto.request.CreateScheduleTemplateRequest;
import dev.knalis.schedule.dto.request.ImportScheduleTemplatesRequest;
import dev.knalis.schedule.dto.request.UpdateScheduleTemplateRequest;
import dev.knalis.schedule.dto.response.ScheduleTemplateResponse;
import dev.knalis.schedule.dto.response.ScheduleTemplateImportResponse;
import dev.knalis.schedule.service.template.ScheduleTemplateService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedule/templates")
@RequiredArgsConstructor
public class ScheduleTemplateController {
    
    private final ScheduleTemplateService scheduleTemplateService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ScheduleTemplateResponse createTemplate(Authentication authentication, @Valid @RequestBody CreateScheduleTemplateRequest request) {
        return scheduleTemplateService.createTemplate(currentUserService.getCurrentUserId(authentication), request);
    }
    
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<ScheduleTemplateResponse> createTemplatesBulk(
            Authentication authentication,
            @Valid @RequestBody BulkCreateScheduleTemplatesRequest request
    ) {
        return scheduleTemplateService.createTemplatesBulk(currentUserService.getCurrentUserId(authentication), request);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ScheduleTemplateImportResponse importTemplates(
            Authentication authentication,
            @Valid @RequestBody ImportScheduleTemplatesRequest request
    ) {
        return scheduleTemplateService.importTemplates(currentUserService.getCurrentUserId(authentication), request);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ScheduleTemplateResponse updateTemplate(
            Authentication authentication,
            @PathVariable("id") UUID templateId,
            @Valid @RequestBody UpdateScheduleTemplateRequest request
    ) {
        return scheduleTemplateService.updateTemplate(currentUserService.getCurrentUserId(authentication), templateId, request);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<Void> deleteTemplate(Authentication authentication, @PathVariable("id") UUID templateId) {
        scheduleTemplateService.deleteTemplate(currentUserService.getCurrentUserId(authentication), templateId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ScheduleTemplateResponse restoreTemplate(Authentication authentication, @PathVariable("id") UUID templateId) {
        return scheduleTemplateService.restoreTemplate(currentUserService.getCurrentUserId(authentication), templateId);
    }
    
    @GetMapping("/semester/{semesterId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<ScheduleTemplateResponse> getTemplatesBySemester(@PathVariable UUID semesterId) {
        return scheduleTemplateService.getTemplatesBySemester(semesterId);
    }
    
    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<ScheduleTemplateResponse> getTemplatesByGroup(@PathVariable UUID groupId) {
        return scheduleTemplateService.getTemplatesByGroup(groupId);
    }
}
