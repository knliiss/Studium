package dev.knalis.assignment.controller;

import dev.knalis.assignment.dto.request.CreateGradeRequest;
import dev.knalis.assignment.dto.response.GradeResponse;
import dev.knalis.assignment.service.grade.GradeService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grades")
@RequiredArgsConstructor
public class GradeController {
    
    private final GradeService gradeService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public GradeResponse createGrade(Authentication authentication, @Valid @RequestBody CreateGradeRequest request) {
        return gradeService.createGrade(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                request
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OWNER".equals(authority) || "ROLE_ADMIN".equals(authority));
    }
}
