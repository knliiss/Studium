package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateLectureAttachmentRequest;
import dev.knalis.education.dto.request.CreateLectureRequest;
import dev.knalis.education.dto.request.MoveLectureRequest;
import dev.knalis.education.dto.request.UpdateLectureRequest;
import dev.knalis.education.dto.response.LectureAttachmentResponse;
import dev.knalis.education.dto.response.LecturePageResponse;
import dev.knalis.education.dto.response.LectureResponse;
import dev.knalis.education.service.lecture.LectureService;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/education")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;
    private final CurrentUserService currentUserService;

    @PostMapping("/subjects/{subjectId}/topics/{topicId}/lectures")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse createLecture(
            Authentication authentication,
            @PathVariable UUID subjectId,
            @PathVariable UUID topicId,
            @Valid @RequestBody CreateLectureRequest request
    ) {
        return lectureService.createLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                subjectId,
                topicId,
                request
        );
    }

    @GetMapping("/lectures/{lectureId}")
    public LectureResponse getLecture(Authentication authentication, @PathVariable UUID lectureId) {
        return lectureService.getLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @GetMapping("/topics/{topicId}/lectures")
    public LecturePageResponse getLecturesByTopic(
            Authentication authentication,
            @PathVariable UUID topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "orderIndex") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return lectureService.getLecturesByTopic(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                topicId,
                page,
                size,
                sortBy,
                direction
        );
    }

    @PutMapping("/lectures/{lectureId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse updateLecture(
            Authentication authentication,
            @PathVariable UUID lectureId,
            @Valid @RequestBody UpdateLectureRequest request
    ) {
        return lectureService.updateLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId,
                request
        );
    }

    @PostMapping("/lectures/{lectureId}/publish")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse publishLecture(Authentication authentication, @PathVariable UUID lectureId) {
        return lectureService.publishLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @PostMapping("/lectures/{lectureId}/close")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse closeLecture(Authentication authentication, @PathVariable UUID lectureId) {
        return lectureService.closeLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @PostMapping("/lectures/{lectureId}/reopen")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse reopenLecture(Authentication authentication, @PathVariable UUID lectureId) {
        return lectureService.reopenLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @PostMapping("/lectures/{lectureId}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse archiveLecture(Authentication authentication, @PathVariable UUID lectureId) {
        return lectureService.archiveLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @PostMapping("/lectures/{lectureId}/restore")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse restoreLecture(Authentication authentication, @PathVariable UUID lectureId) {
        return lectureService.restoreLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @DeleteMapping("/lectures/{lectureId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public void deleteLecture(Authentication authentication, @PathVariable UUID lectureId) {
        lectureService.deleteLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @PatchMapping("/lectures/{lectureId}/position")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureResponse moveLecture(
            Authentication authentication,
            @PathVariable UUID lectureId,
            @Valid @RequestBody MoveLectureRequest request
    ) {
        return lectureService.moveLecture(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId,
                request.topicId(),
                request.orderIndex()
        );
    }

    @GetMapping("/lectures/{lectureId}/attachments")
    public List<LectureAttachmentResponse> listAttachments(
            Authentication authentication,
            @PathVariable UUID lectureId
    ) {
        return lectureService.listAttachments(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId
        );
    }

    @PostMapping("/lectures/{lectureId}/attachments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public LectureAttachmentResponse addAttachment(
            Authentication authentication,
            @PathVariable UUID lectureId,
            @Valid @RequestBody CreateLectureAttachmentRequest request
    ) {
        String bearerToken = currentUserService.getCurrentTokenValue(authentication);
        return lectureService.addAttachment(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                bearerToken,
                lectureId,
                request
        );
    }

    @DeleteMapping("/lectures/{lectureId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public void removeAttachment(
            Authentication authentication,
            @PathVariable UUID lectureId,
            @PathVariable UUID attachmentId
    ) {
        lectureService.removeAttachment(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId,
                attachmentId
        );
    }

    @GetMapping("/lectures/{lectureId}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(
            Authentication authentication,
            @PathVariable UUID lectureId,
            @PathVariable UUID attachmentId
    ) {
        return lectureService.downloadAttachment(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId,
                attachmentId,
                false
        );
    }

    @GetMapping("/lectures/{lectureId}/attachments/{attachmentId}/preview")
    public ResponseEntity<byte[]> previewAttachment(
            Authentication authentication,
            @PathVariable UUID lectureId,
            @PathVariable UUID attachmentId
    ) {
        return lectureService.downloadAttachment(
                currentUserService.getCurrentUserId(authentication),
                currentRoles(authentication),
                lectureId,
                attachmentId,
                true
        );
    }

    private Set<String> currentRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
