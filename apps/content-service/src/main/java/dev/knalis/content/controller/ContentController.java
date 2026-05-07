package dev.knalis.content.controller;

import dev.knalis.content.dto.request.CreateLectureMaterialRequest;
import dev.knalis.content.dto.request.CreateLectureRequest;
import dev.knalis.content.dto.request.CreateTopicMaterialRequest;
import dev.knalis.content.dto.request.UpdateLectureRequest;
import dev.knalis.content.dto.response.LectureMaterialResponse;
import dev.knalis.content.dto.response.LectureResponse;
import dev.knalis.content.dto.response.TopicMaterialResponse;
import dev.knalis.content.service.ContentService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;
    private final CurrentUserService currentUserService;

    @PostMapping("/topic-materials")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public TopicMaterialResponse createTopicMaterial(
            Authentication authentication,
            @Valid @RequestBody CreateTopicMaterialRequest request
    ) {
        return contentService.createTopicMaterial(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                request
        );
    }

    @GetMapping("/topic-materials/topic/{topicId}")
    public List<TopicMaterialResponse> getTopicMaterials(Authentication authentication, @PathVariable UUID topicId) {
        return contentService.getTopicMaterials(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                isTeacher(authentication),
                topicId
        );
    }

    @DeleteMapping("/topic-materials/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public void deleteTopicMaterial(Authentication authentication, @PathVariable("id") UUID topicMaterialId) {
        contentService.deleteTopicMaterial(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                topicMaterialId
        );
    }

    @PostMapping("/lectures")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse createLecture(Authentication authentication, @Valid @RequestBody CreateLectureRequest request) {
        return contentService.createLecture(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                request
        );
    }

    @PatchMapping("/lectures/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse updateLecture(
            Authentication authentication,
            @PathVariable("id") UUID lectureId,
            @Valid @RequestBody UpdateLectureRequest request
    ) {
        return contentService.updateLecture(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                lectureId,
                request
        );
    }

    @PostMapping("/lectures/{id}/publish")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse publishLecture(Authentication authentication, @PathVariable("id") UUID lectureId) {
        return contentService.publishLecture(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                lectureId
        );
    }

    @PostMapping("/lectures/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse archiveLecture(Authentication authentication, @PathVariable("id") UUID lectureId) {
        return contentService.archiveLecture(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                lectureId
        );
    }

    @GetMapping("/lectures/{id}")
    public LectureResponse getLecture(Authentication authentication, @PathVariable("id") UUID lectureId) {
        return contentService.getLecture(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                isTeacher(authentication),
                lectureId
        );
    }

    @GetMapping("/lectures/topic/{topicId}")
    public List<LectureResponse> getLecturesByTopic(
            Authentication authentication,
            @PathVariable UUID topicId
    ) {
        return contentService.getLecturesByTopic(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                isTeacher(authentication),
                topicId
        );
    }

    @PostMapping("/lectures/{id}/materials")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureMaterialResponse addLectureMaterial(
            Authentication authentication,
            @PathVariable("id") UUID lectureId,
            @Valid @RequestBody CreateLectureMaterialRequest request
    ) {
        return contentService.addLectureMaterial(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                lectureId,
                request
        );
    }

    @GetMapping("/lectures/{id}/materials")
    public List<LectureMaterialResponse> getLectureMaterials(Authentication authentication, @PathVariable("id") UUID lectureId) {
        return contentService.getLectureMaterials(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                isTeacher(authentication),
                lectureId
        );
    }

    @DeleteMapping("/lecture-materials/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public void deleteLectureMaterial(Authentication authentication, @PathVariable("id") UUID lectureMaterialId) {
        contentService.deleteLectureMaterial(
                currentUserService.getCurrentUserId(authentication),
                hasManagementBypass(authentication),
                lectureMaterialId
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

