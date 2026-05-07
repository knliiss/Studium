package dev.knalis.assignment.service.attachment;

import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.internal.FileServiceInternalClient;
import dev.knalis.assignment.dto.request.CreateAssignmentAttachmentRequest;
import dev.knalis.assignment.dto.response.AssignmentAttachmentResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentAttachment;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.exception.AssignmentAttachmentNotFoundException;
import dev.knalis.assignment.exception.AssignmentFileAccessDeniedException;
import dev.knalis.assignment.exception.AssignmentNotAccessibleException;
import dev.knalis.assignment.exception.AssignmentNotEditableException;
import dev.knalis.assignment.exception.AssignmentNotFoundException;
import dev.knalis.assignment.exception.FileAttachmentNotAllowedException;
import dev.knalis.assignment.repository.AssignmentAttachmentRepository;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
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
public class AssignmentAttachmentService {

    private static final Set<AssignmentStatus> STUDENT_VISIBLE_STATUSES = Set.of(
            AssignmentStatus.PUBLISHED,
            AssignmentStatus.CLOSED
    );
    private static final Set<String> ALLOWED_FILE_KINDS = Set.of("ATTACHMENT", "DOCUMENT", "GENERIC");

    private final AssignmentRepository assignmentRepository;
    private final AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;
    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final EducationServiceClient educationServiceClient;
    private final FileServiceClient fileServiceClient;
    private final FileServiceInternalClient fileServiceInternalClient;

    @Transactional(readOnly = true)
    public List<AssignmentAttachmentResponse> listAttachments(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID assignmentId
    ) {
        Assignment assignment = requireAssignment(assignmentId);
        ensureCanReadAssignment(assignment, currentUserId, privilegedAccess, teacherAccess);
        return assignmentAttachmentRepository.findAllByAssignmentIdOrderByCreatedAtAsc(assignmentId).stream()
                .map(attachment -> toResponse(attachment, fileServiceInternalClient.getMetadata(attachment.getFileId())))
                .toList();
    }

    @Transactional
    public AssignmentAttachmentResponse addAttachment(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId,
            String bearerToken,
            CreateAssignmentAttachmentRequest request
    ) {
        Assignment assignment = requireAssignment(assignmentId);
        ensureCanManageAssignment(assignment, currentUserId, privilegedAccess);
        if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            throw new AssignmentNotEditableException(assignmentId, assignment.getStatus());
        }
        RemoteStoredFileResponse file = fileServiceClient.getMyFile(bearerToken, request.fileId());
        if (!ALLOWED_FILE_KINDS.contains(file.fileKind())) {
            throw new FileAttachmentNotAllowedException(request.fileId());
        }
        fileServiceClient.markFileActive(bearerToken, request.fileId());
        AssignmentAttachment attachment = new AssignmentAttachment();
        attachment.setAssignmentId(assignmentId);
        attachment.setFileId(request.fileId());
        attachment.setDisplayName(normalizeDisplayName(request.displayName()));
        attachment.setUploadedByUserId(currentUserId);
        AssignmentAttachment savedAttachment = assignmentAttachmentRepository.save(attachment);
        return toResponse(savedAttachment, file);
    }

    @Transactional
    public void removeAttachment(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId,
            UUID attachmentId
    ) {
        Assignment assignment = requireAssignment(assignmentId);
        ensureCanManageAssignment(assignment, currentUserId, privilegedAccess);
        if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            throw new AssignmentNotEditableException(assignmentId, assignment.getStatus());
        }
        AssignmentAttachment attachment = assignmentAttachmentRepository.findByIdAndAssignmentId(attachmentId, assignmentId)
                .orElseThrow(() -> new AssignmentAttachmentNotFoundException(attachmentId));
        assignmentAttachmentRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadAttachment(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID assignmentId,
            UUID attachmentId,
            boolean preview
    ) {
        Assignment assignment = requireAssignment(assignmentId);
        ensureCanReadAssignment(assignment, currentUserId, privilegedAccess, teacherAccess);
        AssignmentAttachment attachment = assignmentAttachmentRepository.findByIdAndAssignmentId(attachmentId, assignmentId)
                .orElseThrow(() -> new AssignmentAttachmentNotFoundException(attachmentId));
        return fileServiceInternalClient.download(attachment.getFileId(), preview);
    }

    private Assignment requireAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
    }

    private void ensureCanReadAssignment(
            Assignment assignment,
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess
    ) {
        if (privilegedAccess) {
            return;
        }
        if (teacherAccess && canManageAssignment(assignment, currentUserId, false)) {
            return;
        }
        if (isAvailableForStudent(assignment.getId(), assignment.getStatus(), currentUserId)) {
            return;
        }
        throw teacherAccess
                ? new AssignmentFileAccessDeniedException()
                : new AssignmentNotAccessibleException(assignment.getId());
    }

    private void ensureCanManageAssignment(Assignment assignment, UUID currentUserId, boolean privilegedAccess) {
        if (canManageAssignment(assignment, currentUserId, privilegedAccess)) {
            return;
        }
        throw new AssignmentFileAccessDeniedException();
    }

    private boolean canManageAssignment(Assignment assignment, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return true;
        }
        if (assignment.getCreatedByUserId() != null && assignment.getCreatedByUserId().equals(currentUserId)) {
            return true;
        }
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        return educationServiceClient.getSubject(subjectId).teacherIds().contains(currentUserId);
    }

    private boolean isAvailableForStudent(UUID assignmentId, AssignmentStatus status, UUID currentUserId) {
        if (!STUDENT_VISIBLE_STATUSES.contains(status)) {
            return false;
        }
        Set<UUID> groupIds = educationServiceClient.getGroupsByUser(currentUserId).stream()
                .map(groupMembership -> groupMembership.groupId())
                .collect(Collectors.toSet());
        return !groupIds.isEmpty()
                && assignmentGroupAvailabilityRepository.existsAvailableForGroups(assignmentId, groupIds, Instant.now());
    }

    private AssignmentAttachmentResponse toResponse(AssignmentAttachment attachment, RemoteStoredFileResponse file) {
        return new AssignmentAttachmentResponse(
                attachment.getId(),
                attachment.getAssignmentId(),
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
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        return normalizedContentType.equals("application/pdf") || normalizedContentType.startsWith("image/");
    }
}
