package dev.knalis.testing.controller;

import dev.knalis.shared.security.user.CurrentUserService;
import dev.knalis.testing.dto.request.CreateTestResultRequest;
import dev.knalis.testing.dto.request.OverrideTestResultScoreRequest;
import dev.knalis.testing.dto.response.TestResultPageResponse;
import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.service.result.TestResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/testing/results")
@RequiredArgsConstructor
public class TestResultController {
    
    private final TestResultService testResultService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public TestResultResponse createTestResult(
            Authentication authentication,
            @Valid @RequestBody CreateTestResultRequest request
    ) {
        return testResultService.createTestResult(
                currentUserService.getCurrentUserId(authentication),
                request
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResultResponse getTestResult(Authentication authentication, @PathVariable("id") UUID resultId) {
        return testResultService.getTestResult(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                resultId
        );
    }

    @GetMapping("/test/{testId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResultPageResponse getTestResultsByTest(
            Authentication authentication,
            @PathVariable("testId") UUID testId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return testResultService.getTestResultsByTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId,
                page,
                size
        );
    }

    @PatchMapping("/{id}/score")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResultResponse overrideTestResultScore(
            Authentication authentication,
            @PathVariable("id") UUID resultId,
            @Valid @RequestBody OverrideTestResultScoreRequest request
    ) {
        return testResultService.overrideTestResultScore(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                resultId,
                request
        );
    }

    private boolean hasManagementBypass(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OWNER".equals(authority)
                        || "ROLE_ADMIN".equals(authority));
    }
}
