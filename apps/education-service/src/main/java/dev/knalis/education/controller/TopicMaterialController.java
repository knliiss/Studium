package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateTopicMaterialRequest;
import dev.knalis.education.dto.request.MoveTopicMaterialRequest;
import dev.knalis.education.dto.request.UpdateTopicMaterialRequest;
import dev.knalis.education.dto.response.TopicMaterialPageResponse;
import dev.knalis.education.dto.response.TopicMaterialResponse;
import dev.knalis.education.service.material.TopicMaterialService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/education")
@RequiredArgsConstructor
public class TopicMaterialController {

    private final TopicMaterialService topicMaterialService;
    private final CurrentUserService currentUserService;

    @GetMapping("/topics/{topicId}/materials")
    public TopicMaterialPageResponse getMaterialsByTopic(
            Authentication authentication,
            @PathVariable UUID topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "orderIndex") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return topicMaterialService.getMaterialsByTopic(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                topicId,
                page,
                size,
                sortBy,
                direction
        );
    }

    @PostMapping("/topics/{topicId}/materials")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse createMaterial(
            Authentication authentication,
            @PathVariable UUID topicId,
            @Valid @RequestBody CreateTopicMaterialRequest request
    ) {
        return topicMaterialService.createMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                topicId,
                request
        );
    }

    @GetMapping("/materials/{materialId}")
    public TopicMaterialResponse getMaterial(Authentication authentication, @PathVariable UUID materialId) {
        return topicMaterialService.getMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId
        );
    }

    @PutMapping("/materials/{materialId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse updateMaterial(
            Authentication authentication,
            @PathVariable UUID materialId,
            @Valid @RequestBody UpdateTopicMaterialRequest request
    ) {
        return topicMaterialService.updateMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                materialId,
                request
        );
    }

    @PostMapping("/materials/{materialId}/publish")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse publishMaterial(Authentication authentication, @PathVariable UUID materialId) {
        return topicMaterialService.publishMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId
        );
    }

    @PostMapping("/materials/{materialId}/hide")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse hideMaterial(Authentication authentication, @PathVariable UUID materialId) {
        return topicMaterialService.hideMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId
        );
    }

    @PostMapping("/materials/{materialId}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse archiveMaterial(Authentication authentication, @PathVariable UUID materialId) {
        return topicMaterialService.archiveMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId
        );
    }

    @PostMapping("/materials/{materialId}/restore")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse restoreMaterial(Authentication authentication, @PathVariable UUID materialId) {
        return topicMaterialService.restoreMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId
        );
    }

    @DeleteMapping("/materials/{materialId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public void deleteMaterial(Authentication authentication, @PathVariable UUID materialId) {
        topicMaterialService.deleteMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId
        );
    }

    @PatchMapping("/materials/{materialId}/position")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse moveMaterial(
            Authentication authentication,
            @PathVariable UUID materialId,
            @Valid @RequestBody MoveTopicMaterialRequest request
    ) {
        return topicMaterialService.moveMaterial(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId,
                request.topicId(),
                request.orderIndex()
        );
    }

    @GetMapping("/materials/{materialId}/download")
    public ResponseEntity<byte[]> downloadMaterialFile(Authentication authentication, @PathVariable UUID materialId) {
        return topicMaterialService.downloadFile(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId,
                false
        );
    }

    @GetMapping("/materials/{materialId}/preview")
    public ResponseEntity<byte[]> previewMaterialFile(Authentication authentication, @PathVariable UUID materialId) {
        return topicMaterialService.downloadFile(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                materialId,
                true
        );
    }

    private Set<String> currentRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
