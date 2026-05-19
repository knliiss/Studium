package dev.knalis.assignment.controller;

import dev.knalis.assignment.dto.request.CreateSubmissionRequest;
import dev.knalis.assignment.dto.request.CreateSubmissionAttachmentRequest;
import dev.knalis.assignment.dto.request.ResubmitSubmissionRequest;
import dev.knalis.assignment.dto.request.ReturnSubmissionForRevisionRequest;
import dev.knalis.assignment.dto.request.UpsertSubmissionCommentRequest;
import dev.knalis.assignment.dto.response.SubmissionAttachmentResponse;
import dev.knalis.assignment.dto.response.SubmissionCommentResponse;
import dev.knalis.assignment.dto.response.SubmissionDetailResponse;
import dev.knalis.assignment.dto.response.SubmissionPageResponse;
import dev.knalis.assignment.dto.response.SubmissionResponse;
import dev.knalis.assignment.service.attachment.SubmissionAttachmentService;
import dev.knalis.assignment.service.comment.SubmissionCommentService;
import dev.knalis.assignment.service.submission.SubmissionService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {
    
    private final SubmissionService submissionService;
    private final SubmissionAttachmentService submissionAttachmentService;
    private final SubmissionCommentService submissionCommentService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public SubmissionResponse createSubmission(
            Authentication authentication,
            @Valid @RequestBody CreateSubmissionRequest request
    ) {
        return submissionService.createSubmission(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                request
        );
    }

    @PostMapping("/{submissionId}/resubmit")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public SubmissionResponse resubmitSubmission(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @Valid @RequestBody ResubmitSubmissionRequest request
    ) {
        return submissionService.resubmitSubmission(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                submissionId,
                request
        );
    }
    
    @GetMapping("/assignment/{assignmentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public SubmissionPageResponse getSubmissionsByAssignment(
            Authentication authentication,
            @PathVariable UUID assignmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return submissionService.getSubmissionsByAssignment(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                assignmentId,
                page,
                size,
                sortBy,
                direction
        );
    }

    @GetMapping("/review")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public SubmissionPageResponse getReviewQueue(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID assignmentId,
            @RequestParam(required = false) String status
    ) {
        return submissionService.getReviewQueue(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                page,
                size,
                assignmentId,
                status
        );
    }

    @GetMapping("/assignment/{assignmentId}/mine")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STUDENT')")
    public List<SubmissionResponse> getMySubmissionsByAssignment(
            Authentication authentication,
            @PathVariable UUID assignmentId
    ) {
        return submissionService.getMySubmissionsByAssignment(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                assignmentId
        );
    }

    @GetMapping("/{submissionId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public SubmissionResponse getSubmission(
            Authentication authentication,
            @PathVariable UUID submissionId
    ) {
        return submissionService.getSubmission(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId
        );
    }

    @GetMapping("/{submissionId}/workflow")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public SubmissionDetailResponse getSubmissionDetail(
            Authentication authentication,
            @PathVariable UUID submissionId
    ) {
        return submissionService.getSubmissionDetail(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId
        );
    }

    @PostMapping("/{submissionId}/return-for-revision")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public SubmissionResponse returnForRevision(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @Valid @RequestBody ReturnSubmissionForRevisionRequest request
    ) {
        return submissionService.returnForRevision(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                submissionId,
                request
        );
    }

    @PostMapping("/{submissionId}/comments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public SubmissionCommentResponse createComment(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @Valid @RequestBody UpsertSubmissionCommentRequest request
    ) {
        return submissionCommentService.createComment(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId,
                request
        );
    }

    @GetMapping("/{submissionId}/comments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<SubmissionCommentResponse> getComments(
            Authentication authentication,
            @PathVariable UUID submissionId
    ) {
        return submissionCommentService.getComments(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId
        );
    }

    @PutMapping("/{submissionId}/comments/{commentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public SubmissionCommentResponse updateComment(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpsertSubmissionCommentRequest request
    ) {
        return submissionCommentService.updateComment(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId,
                commentId,
                request
        );
    }

    @DeleteMapping("/{submissionId}/comments/{commentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public void deleteComment(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @PathVariable UUID commentId
    ) {
        submissionCommentService.deleteComment(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId,
                commentId
        );
    }

    @GetMapping("/{submissionId}/attachments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<SubmissionAttachmentResponse> listAttachments(
            Authentication authentication,
            @PathVariable UUID submissionId
    ) {
        return submissionAttachmentService.listAttachments(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId
        );
    }

    @PostMapping("/{submissionId}/attachments")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public SubmissionAttachmentResponse addAttachment(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @Valid @RequestBody CreateSubmissionAttachmentRequest request
    ) {
        return submissionAttachmentService.addAttachment(
                currentUserService.getCurrentUserId(authentication),
                currentUserService.getCurrentTokenValue(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId,
                request
        );
    }

    @DeleteMapping("/{submissionId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public void removeAttachment(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @PathVariable UUID attachmentId
    ) {
        submissionAttachmentService.removeAttachment(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId,
                attachmentId
        );
    }

    @GetMapping("/{submissionId}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<byte[]> downloadAttachment(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @PathVariable UUID attachmentId
    ) {
        return submissionAttachmentService.downloadAttachment(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId,
                attachmentId,
                false
        );
    }

    @GetMapping("/{submissionId}/attachments/{attachmentId}/preview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public ResponseEntity<byte[]> previewAttachment(
            Authentication authentication,
            @PathVariable UUID submissionId,
            @PathVariable UUID attachmentId
    ) {
        return submissionAttachmentService.downloadAttachment(
                currentUserService.getCurrentUserId(authentication),
                isAdmin(authentication),
                isTeacher(authentication),
                submissionId,
                attachmentId,
                true
        );
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OWNER".equals(authority) || "ROLE_ADMIN".equals(authority));
    }

    private boolean isTeacher(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_TEACHER"::equals);
    }
}
