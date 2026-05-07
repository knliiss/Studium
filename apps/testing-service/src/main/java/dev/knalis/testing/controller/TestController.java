package dev.knalis.testing.controller;

import dev.knalis.testing.dto.request.CreateTestRequest;
import dev.knalis.testing.dto.request.MoveTestRequest;
import dev.knalis.testing.dto.request.SubmitTestAttemptRequest;
import dev.knalis.testing.dto.request.UpsertTestGroupAvailabilityRequest;
import dev.knalis.testing.dto.response.SearchPageResponse;
import dev.knalis.testing.dto.response.TestGroupAvailabilityResponse;
import dev.knalis.testing.dto.response.TestPageResponse;
import dev.knalis.testing.dto.response.TestResponse;
import dev.knalis.testing.dto.response.TestStudentViewResponse;
import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.service.test.TestService;
import dev.knalis.testing.service.result.TestResultService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/testing/tests")
@RequiredArgsConstructor
public class TestController {
    
    private final TestService testService;
    private final TestResultService testResultService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResponse createTest(Authentication authentication, @Valid @RequestBody CreateTestRequest request) {
        return testService.createTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                request
        );
    }
    
    @GetMapping("/topic/{topicId}")
    public TestPageResponse getTestsByTopic(
            Authentication authentication,
            @PathVariable UUID topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return testService.getTestsByTopic(
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
    public TestResponse getTest(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.getTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                isTeacher(authentication),
                testId
        );
    }

    @GetMapping("/{id}/student-view")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public TestStudentViewResponse getStudentView(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.getStudentView(
                currentUserService.getCurrentUserId(authentication),
                testId
        );
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestStudentViewResponse getPreviewView(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.getPreviewView(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<TestGroupAvailabilityResponse> getTestAvailability(
            Authentication authentication,
            @PathVariable("id") UUID testId
    ) {
        return testService.getTestAvailability(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestGroupAvailabilityResponse upsertTestAvailability(
            Authentication authentication,
            @PathVariable("id") UUID testId,
            @Valid @RequestBody UpsertTestGroupAvailabilityRequest request
    ) {
        return testService.upsertTestAvailability(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId,
                request
        );
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SearchPageResponse searchTests(
            Authentication authentication,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return testService.searchTests(q, page, size, sortBy, direction, hasManagementBypass(authentication));
    }
    
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public void startTest(Authentication authentication, @PathVariable("id") UUID testId) {
        testService.startTest(currentUserService.getCurrentUserId(authentication), testId);
    }

    @PostMapping("/{id}/finish")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public TestResultResponse finishTest(
            Authentication authentication,
            @PathVariable("id") UUID testId,
            @Valid @RequestBody SubmitTestAttemptRequest request
    ) {
        return testResultService.submitTestAttempt(
                currentUserService.getCurrentUserId(authentication),
                testId,
                request
        );
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResponse publishTest(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.publishTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResponse closeTest(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.closeTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResponse reopenTest(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.reopenTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResponse archiveTest(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.archiveTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResponse restoreTest(Authentication authentication, @PathVariable("id") UUID testId) {
        return testService.restoreTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public void deleteTest(Authentication authentication, @PathVariable("id") UUID testId) {
        testService.deleteTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId
        );
    }

    @PatchMapping("/{id}/position")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TestResponse moveTest(
            Authentication authentication,
            @PathVariable("id") UUID testId,
            @Valid @RequestBody MoveTestRequest request
    ) {
        return testService.moveTest(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                testId,
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
