package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateSubjectRequest;
import dev.knalis.education.dto.request.UpdateSubjectGroupsRequest;
import dev.knalis.education.dto.request.UpdateSubjectRequest;
import dev.knalis.education.dto.request.UpdateSubjectTeachersRequest;
import dev.knalis.education.dto.response.SubjectPageResponse;
import dev.knalis.education.dto.response.SubjectResponse;
import dev.knalis.education.service.subject.SubjectService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/education/subjects")
@RequiredArgsConstructor
public class SubjectController {
    
    private final SubjectService subjectService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SubjectResponse createSubject(Authentication authentication, @Valid @RequestBody CreateSubjectRequest request) {
        return subjectService.createSubject(currentUserService.getCurrentUserId(authentication), request);
    }
    
    @GetMapping("/{id}")
    public SubjectResponse getSubject(Authentication authentication, @PathVariable("id") UUID subjectId) {
        return subjectService.getSubject(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                subjectId
        );
    }

    @GetMapping
    public SubjectPageResponse listSubjects(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String q
    ) {
        return subjectService.listSubjects(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                page,
                size,
                sortBy,
                direction,
                q
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SubjectResponse updateSubject(
            Authentication authentication,
            @PathVariable("id") UUID subjectId,
            @Valid @RequestBody UpdateSubjectRequest request
    ) {
        return subjectService.updateSubject(currentUserService.getCurrentUserId(authentication), subjectId, request);
    }

    @GetMapping("/{id}/groups")
    public List<UUID> getSubjectGroups(Authentication authentication, @PathVariable("id") UUID subjectId) {
        return subjectService.getSubjectGroups(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                subjectId
        );
    }

    @PutMapping("/{id}/groups")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SubjectResponse updateSubjectGroups(
            Authentication authentication,
            @PathVariable("id") UUID subjectId,
            @Valid @RequestBody UpdateSubjectGroupsRequest request
    ) {
        return subjectService.updateSubjectGroups(
                currentUserService.getCurrentUserId(authentication),
                subjectId,
                request
        );
    }

    @GetMapping("/{id}/teachers")
    public List<UUID> getSubjectTeachers(Authentication authentication, @PathVariable("id") UUID subjectId) {
        return subjectService.getSubjectTeachers(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                subjectId
        );
    }

    @PutMapping("/{id}/teachers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SubjectResponse updateSubjectTeachers(
            Authentication authentication,
            @PathVariable("id") UUID subjectId,
            @Valid @RequestBody UpdateSubjectTeachersRequest request
    ) {
        return subjectService.updateSubjectTeachers(
                currentUserService.getCurrentUserId(authentication),
                subjectId,
                request
        );
    }
    
    @GetMapping("/group/{groupId}")
    public SubjectPageResponse getSubjectsByGroup(
            Authentication authentication,
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return subjectService.getSubjectsByGroup(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                groupId,
                page,
                size,
                sortBy,
                direction
        );
    }

    private Set<String> currentRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
