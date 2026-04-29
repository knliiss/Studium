package dev.knalis.testing.controller;

import dev.knalis.testing.dto.request.CreateQuestionRequest;
import dev.knalis.testing.dto.response.QuestionResponse;
import dev.knalis.shared.security.user.CurrentUserService;
import dev.knalis.testing.service.question.QuestionService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/testing/questions")
@RequiredArgsConstructor
public class QuestionController {
    
    private final QuestionService questionService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public QuestionResponse createQuestion(Authentication authentication, @Valid @RequestBody CreateQuestionRequest request) {
        return questionService.createQuestion(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                request
        );
    }

    @GetMapping("/test/{testId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<QuestionResponse> getQuestionsByTest(Authentication authentication, @PathVariable UUID testId) {
        return questionService.getQuestionsByTest(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                testId
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OWNER".equals(authority) || "ROLE_ADMIN".equals(authority));
    }
}
