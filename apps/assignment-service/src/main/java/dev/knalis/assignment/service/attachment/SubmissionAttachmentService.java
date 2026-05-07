package dev.knalis.assignment.service.attachment;

import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.internal.FileServiceInternalClient;
import dev.knalis.assignment.dto.request.CreateSubmissionAttachmentRequest;
import dev.knalis.assignment.dto.response.SubmissionAttachmentResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.entity.SubmissionAttachment;
import dev.knalis.assignment.exception.AssignmentNotFoundException;
import dev.knalis.assignment.exception.AssignmentClosedException;
import dev.knalis.assignment.exception.AssignmentInvalidStateException;
import dev.knalis.assignment.exception.FileAttachmentNotAllowedException;
import dev.knalis.assignment.exception.FileTooLargeException;
import dev.knalis.assignment.exception.FileTypeNotAllowedException;
import dev.knalis.assignment.exception.ResubmissionNotAllowedException;
import dev.knalis.assignment.exception.SubmissionAttachmentNotFoundException;
import dev.knalis.assignment.exception.SubmissionFileAccessDeniedException;
import dev.knalis.assignment.exception.SubmissionNotAccessibleException;
import dev.knalis.assignment.exception.SubmissionNotFoundException;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionAttachmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionAttachmentService {

    private static final Set<String> ALLOWED_FILE_KINDS = Set.of("ATTACHMENT", "DOCUMENT", "GENERIC");

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final GradeRepository gradeRepository;
    private final EducationServiceClient educationServiceClient;
    private final FileServiceClient fileServiceClient;
    private final FileServiceInternalClient fileServiceInternalClient;

    @Transactional
    public void createInitialAttachment(UUID submissionId, UUID uploadedByUserId, UUID fileId) {
        SubmissionAttachment attachment = new SubmissionAttachment();
        attachment.setSubmissionId(submissionId);
        attachment.setFileId(fileId);
        attachment.setUploadedByUserId(uploadedByUserId);
        submissionAttachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public List<SubmissionAttachmentResponse> listAttachments(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID submissionId
    ) {
        Submission submission = requireSubmission(submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        AccessLevel accessLevel = resolveAccessLevel(submission, assignment, currentUserId, privilegedAccess, teacherAccess);
        if (accessLevel == AccessLevel.NONE) {
            throw new SubmissionNotAccessibleException(submissionId);
        }
        return submissionAttachmentRepository.findAllBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
                .map(attachment -> toResponse(attachment, fileServiceInternalClient.getMetadata(attachment.getFileId())))
                .toList();
    }

    @Transactional
    public SubmissionAttachmentResponse addAttachment(
            UUID currentUserId,
            String bearerToken,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID submissionId,
            CreateSubmissionAttachmentRequest request
    ) {
        Submission submission = requireSubmission(submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        AccessLevel accessLevel = resolveAccessLevel(submission, assignment, currentUserId, privilegedAccess, teacherAccess);
        if (accessLevel != AccessLevel.OWNER) {
            throw new SubmissionFileAccessDeniedException();
        }
        validateSubmissionModificationAllowed(assignment, submission, currentUserId);
        RemoteStoredFileResponse file = fileServiceClient.getMyFile(bearerToken, request.fileId());
        validateSubmissionFile(currentUserId, assignment, file);
        if (!ALLOWED_FILE_KINDS.contains(file.fileKind())) {
            throw new FileAttachmentNotAllowedException(request.fileId());
        }
        fileServiceClient.markFileActive(bearerToken, request.fileId());
        SubmissionAttachment attachment = new SubmissionAttachment();
        attachment.setSubmissionId(submissionId);
        attachment.setFileId(request.fileId());
        attachment.setDisplayName(normalizeDisplayName(request.displayName()));
        attachment.setUploadedByUserId(currentUserId);
        SubmissionAttachment savedAttachment = submissionAttachmentRepository.save(attachment);
        return toResponse(savedAttachment, file);
    }

    @Transactional
    public void removeAttachment(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID submissionId,
            UUID attachmentId
    ) {
        Submission submission = requireSubmission(submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        AccessLevel accessLevel = resolveAccessLevel(submission, assignment, currentUserId, privilegedAccess, teacherAccess);
        if (accessLevel != AccessLevel.OWNER) {
            throw new SubmissionFileAccessDeniedException();
        }
        validateSubmissionModificationAllowed(assignment, submission, currentUserId);
        long attachmentCount = submissionAttachmentRepository.countBySubmissionId(submissionId);
        if (attachmentCount <= 1) {
            throw new ResubmissionNotAllowedException(assignment.getId());
        }
        SubmissionAttachment attachment = submissionAttachmentRepository.findByIdAndSubmissionId(attachmentId, submissionId)
                .orElseThrow(() -> new SubmissionAttachmentNotFoundException(attachmentId));
        submissionAttachmentRepository.delete(attachment);
        if (submission.getFileId().equals(attachment.getFileId())) {
            SubmissionAttachment replacement = submissionAttachmentRepository.findAllBySubmissionIdOrderByCreatedAtAsc(submissionId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new SubmissionAttachmentNotFoundException(attachmentId));
            submission.setFileId(replacement.getFileId());
            submissionRepository.save(submission);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadAttachment(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID submissionId,
            UUID attachmentId,
            boolean preview
    ) {
        Submission submission = requireSubmission(submissionId);
        Assignment assignment = requireAssignment(submission.getAssignmentId());
        AccessLevel accessLevel = resolveAccessLevel(submission, assignment, currentUserId, privilegedAccess, teacherAccess);
        if (accessLevel == AccessLevel.NONE) {
            throw new SubmissionNotAccessibleException(submissionId);
        }
        SubmissionAttachment attachment = submissionAttachmentRepository.findByIdAndSubmissionId(attachmentId, submissionId)
                .orElseThrow(() -> new SubmissionAttachmentNotFoundException(attachmentId));
        return fileServiceInternalClient.download(attachment.getFileId(), preview);
    }

    private void validateSubmissionModificationAllowed(Assignment assignment, Submission submission, UUID currentUserId) {
        if (assignment.getStatus() == AssignmentStatus.CLOSED) {
            throw new AssignmentClosedException(assignment.getId());
        }
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new AssignmentInvalidStateException(
                    assignment.getId(),
                    assignment.getStatus(),
                    "Submission modifications are allowed only for published assignments"
            );
        }
        if (gradeRepository.findBySubmissionId(submission.getId()).isPresent()) {
            throw new ResubmissionNotAllowedException(assignment.getId());
        }
        AssignmentGroupAvailability availability = resolveAvailability(assignment, submission.getId(), currentUserId, Instant.now());
        if (!availability.isAllowLateSubmissions() && Instant.now().isAfter(availability.getDeadline())) {
            throw new ResubmissionNotAllowedException(assignment.getId());
        }
    }

    private AssignmentGroupAvailability resolveAvailability(Assignment assignment, UUID submissionId, UUID userId, Instant now) {
        Set<UUID> groupIds = educationServiceClient.getGroupsByUser(userId).stream()
                .map(groupMembership -> groupMembership.groupId())
                .collect(Collectors.toSet());
        List<AssignmentGroupAvailability> availabilityRows = assignmentGroupAvailabilityRepository
                .findAvailableForAssignmentsAndGroups(List.of(assignment.getId()), groupIds, now);
        return availabilityRows.stream()
                .findFirst()
                .orElseThrow(() -> new SubmissionNotAccessibleException(submissionId));
    }

    private void validateSubmissionFile(UUID userId, Assignment assignment, RemoteStoredFileResponse file) {
        if (!userId.equals(file.ownerId())) {
            throw new SubmissionFileAccessDeniedException();
        }
        if (!assignment.getAcceptedFileTypes().isEmpty()) {
            String contentType = file.contentType() == null ? "" : file.contentType().trim().toLowerCase(Locale.ROOT);
            if (!assignment.getAcceptedFileTypes().contains(contentType)) {
                throw new FileTypeNotAllowedException(
                        assignment.getId(),
                        file.id(),
                        file.contentType(),
                        assignment.getAcceptedFileTypes()
                );
            }
        }
        if (assignment.getMaxFileSizeMb() != null
                && file.sizeBytes() > assignment.getMaxFileSizeMb().longValue() * 1024L * 1024L) {
            throw new FileTooLargeException(
                    assignment.getId(),
                    file.id(),
                    file.sizeBytes(),
                    assignment.getMaxFileSizeMb()
            );
        }
    }

    private AccessLevel resolveAccessLevel(
            Submission submission,
            Assignment assignment,
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess
    ) {
        if (privilegedAccess) {
            return AccessLevel.PRIVILEGED;
        }
        if (teacherAccess && canManageAssignment(assignment, currentUserId)) {
            return AccessLevel.TEACHER;
        }
        if (submission.getUserId().equals(currentUserId)) {
            return AccessLevel.OWNER;
        }
        return AccessLevel.NONE;
    }

    private boolean canManageAssignment(Assignment assignment, UUID currentUserId) {
        if (assignment.getCreatedByUserId() != null && assignment.getCreatedByUserId().equals(currentUserId)) {
            return true;
        }
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        return educationServiceClient.getSubject(subjectId).teacherIds().contains(currentUserId);
    }

    private Submission requireSubmission(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
    }

    private Assignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
    }

    private SubmissionAttachmentResponse toResponse(SubmissionAttachment attachment, RemoteStoredFileResponse file) {
        return new SubmissionAttachmentResponse(
                attachment.getId(),
                attachment.getSubmissionId(),
                attachment.getFileId(),
                attachment.getDisplayName(),
                file.originalFileName(),
                file.contentType(),
                file.sizeBytes(),
                isPreviewAvailable(file.contentType()),
                attachment.getUploadedByUserId(),
                attachment.getCreatedAt()
        );
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return displayName.trim();
    }

    private boolean isPreviewAvailable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/pdf") || normalized.startsWith("image/");
    }

    private enum AccessLevel {
        NONE,
        OWNER,
        TEACHER,
        PRIVILEGED
    }
}
