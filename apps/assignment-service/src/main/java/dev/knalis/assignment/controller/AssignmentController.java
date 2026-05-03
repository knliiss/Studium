package dev.knalis.assignment.controller;

import dev.knalis.assignment.dto.request.BulkUpsertAssignmentGroupAvailabilityRequest;
import dev.knalis.assignment.dto.request.CreateAssignmentRequest;
import dev.knalis.assignment.dto.request.MoveAssignmentRequest;
import dev.knalis.assignment.dto.request.UpdateAssignmentRequest;
import dev.knalis.assignment.dto.request.UpsertAssignmentGroupAvailabilityRequest;
import dev.knalis.assignment.dto.response.AssignmentGroupAvailabilityResponse;
import dev.knalis.assignment.dto.response.AssignmentPageResponse;
import dev.knalis.assignment.dto.response.AssignmentResponse;
import dev.knalis.assignment.dto.response.SearchPageResponse;
import dev.knalis.assignment.service.assignment.AssignmentService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {
    
    private final AssignmentService assignmentService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public AssignmentResponse createAssignment(
            Authentication authentication,
            @Valid @RequestBody CreateAssignmentRequest request
    ) {
        return assignmentService.createAssignment(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                request
        );
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public AssignmentResponse updateAssignment(
            Authentication authentication,
            @PathVariable("id") UUID assignmentId,
            @Valid @RequestBody UpdateAssignmentRequest request
    ) {
        return assignmentService.updateAssignment(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId,
                request
        );
    }
    
    @GetMapping("/topic/{topicId}")
    public AssignmentPageResponse getAssignmentsByTopic(
            Authentication authentication,
            @PathVariable UUID topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "deadline") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return assignmentService.getAssignmentsByTopic(
                topicId,
                currentUserService.getCurrentUserId(authentication),
                page,
                size,
                sortBy,
                direction,
                hasManagementBypass(authentication),
                isTeacher(authentication)
        );
    }
    
    @GetMapping("/{id}")
    public AssignmentResponse getAssignment(Authentication authentication, @PathVariable("id") UUID assignmentId) {
        return assignmentService.getAssignment(
                currentUserService.getCurrentUserId(authentication),
                assignmentId,
                hasManagementBypass(authentication),
                isTeacher(authentication)
        );
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<AssignmentGroupAvailabilityResponse> getAssignmentAvailability(
            Authentication authentication,
            @PathVariable("id") UUID assignmentId
    ) {
        return assignmentService.getAssignmentAvailability(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId
        );
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public AssignmentGroupAvailabilityResponse upsertAssignmentAvailability(
            Authentication authentication,
            @PathVariable("id") UUID assignmentId,
            @Valid @RequestBody UpsertAssignmentGroupAvailabilityRequest request
    ) {
        return assignmentService.upsertAssignmentAvailability(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId,
                request
        );
    }

    @PutMapping("/{id}/availability/bulk")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<AssignmentGroupAvailabilityResponse> bulkUpsertAssignmentAvailability(
            Authentication authentication,
            @PathVariable("id") UUID assignmentId,
            @Valid @RequestBody BulkUpsertAssignmentGroupAvailabilityRequest request
    ) {
        return assignmentService.bulkUpsertAssignmentAvailability(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId,
                request
        );
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SearchPageResponse searchAssignments(
            Authentication authentication,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "deadline") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return assignmentService.searchAssignments(q, page, size, sortBy, direction, hasManagementBypass(authentication));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public AssignmentResponse publishAssignment(Authentication authentication, @PathVariable("id") UUID assignmentId) {
        return assignmentService.publishAssignment(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId
        );
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public AssignmentResponse archiveAssignment(Authentication authentication, @PathVariable("id") UUID assignmentId) {
        return assignmentService.archiveAssignment(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public void deleteAssignment(Authentication authentication, @PathVariable("id") UUID assignmentId) {
        assignmentService.deleteAssignment(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId
        );
    }

    @PatchMapping("/{id}/position")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public AssignmentResponse moveAssignment(
            Authentication authentication,
            @PathVariable("id") UUID assignmentId,
            @Valid @RequestBody MoveAssignmentRequest request
    ) {
        return assignmentService.moveAssignment(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                assignmentId,
                request
        );
    }

    private boolean hasManagementBypass(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OWNER".equals(authority)
                        || "ROLE_ADMIN".equals(authority));
    }

    private boolean isTeacher(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_TEACHER"::equals);
    }
}
