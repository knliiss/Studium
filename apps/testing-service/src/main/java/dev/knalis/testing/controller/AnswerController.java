package dev.knalis.testing.controller;

import dev.knalis.testing.dto.request.CreateAnswerRequest;
import dev.knalis.testing.dto.response.AnswerResponse;
import dev.knalis.shared.security.user.CurrentUserService;
import dev.knalis.testing.service.answer.AnswerService;
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
@RequestMapping("/api/v1/testing/answers")
@RequiredArgsConstructor
public class AnswerController {
    
    private final AnswerService answerService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public AnswerResponse createAnswer(Authentication authentication, @Valid @RequestBody CreateAnswerRequest request) {
        return answerService.createAnswer(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                request
        );
    }

    @GetMapping("/question/{questionId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<AnswerResponse> getAnswersByQuestion(Authentication authentication, @PathVariable UUID questionId) {
        return answerService.getAnswersByQuestion(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                questionId
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OWNER".equals(authority) || "ROLE_ADMIN".equals(authority));
    }
}
