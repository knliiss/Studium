package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateTopicRequest;
import dev.knalis.education.dto.request.ReorderTopicsRequest;
import dev.knalis.education.dto.request.UpdateTopicRequest;
import dev.knalis.education.dto.response.TopicPageResponse;
import dev.knalis.education.dto.response.TopicResponse;
import dev.knalis.education.service.topic.TopicService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/education/topics")
@RequiredArgsConstructor
public class TopicController {
    
    private final TopicService topicService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicResponse createTopic(Authentication authentication, @Valid @RequestBody CreateTopicRequest request) {
        return topicService.createTopic(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                request
        );
    }

    @PatchMapping("/{topicId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicResponse updateTopic(
            Authentication authentication,
            @PathVariable UUID topicId,
            @Valid @RequestBody UpdateTopicRequest request
    ) {
        return topicService.updateTopic(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                topicId,
                request
        );
    }

    @PatchMapping("/subject/{subjectId}/reorder")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<TopicResponse> reorderTopics(
            Authentication authentication,
            @PathVariable UUID subjectId,
            @Valid @RequestBody ReorderTopicsRequest request
    ) {
        return topicService.reorderTopics(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                subjectId,
                request
        );
    }
    
    @GetMapping("/subject/{subjectId}")
    public TopicPageResponse getTopicsBySubject(
            @PathVariable UUID subjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "orderIndex") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return topicService.getTopicsBySubject(subjectId, page, size, sortBy, direction);
    }

    private Set<String> currentRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
