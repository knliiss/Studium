package dev.knalis.assignment.service.submission;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.dto.request.CreateSubmissionRequest;
import dev.knalis.assignment.dto.response.SubmissionPageResponse;
import dev.knalis.assignment.dto.response.SubmissionFileResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.dto.response.SubmissionResponse;
import dev.knalis.assignment.entity.Grade;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.exception.AssignmentAccessDeniedException;
import dev.knalis.assignment.exception.AssignmentClosedException;
import dev.knalis.assignment.exception.AssignmentNotFoundException;
import dev.knalis.assignment.exception.AssignmentInvalidStateException;
import dev.knalis.assignment.exception.DeadlineExpiredException;
import dev.knalis.assignment.exception.FileTooLargeException;
import dev.knalis.assignment.exception.FileTypeNotAllowedException;
import dev.knalis.assignment.exception.InvalidSubmissionFileException;
import dev.knalis.assignment.exception.MaxSubmissionsExceededException;
import dev.knalis.assignment.exception.SubmissionNotFoundException;
import dev.knalis.assignment.factory.submission.SubmissionFactory;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.attachment.SubmissionAttachmentService;
import dev.knalis.assignment.service.common.AssignmentAuditService;
import dev.knalis.assignment.service.common.AssignmentEventPublisher;
import dev.knalis.contracts.event.AssignmentSubmittedEventV1;
import dev.knalis.contracts.event.DeadlineMissedEntityTypeV1;
import dev.knalis.contracts.event.DeadlineMissedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class SubmissionService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "submittedAt",
            "updatedAt"
    );
    
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;
    private final SubmissionFactory submissionFactory;
    private final FileServiceClient fileServiceClient;
    private final GradeRepository gradeRepository;
    private final AssignmentAuditService assignmentAuditService;
    private final AssignmentEventPublisher assignmentEventPublisher;
    private final EducationServiceClient educationServiceClient;
    private final SubmissionAttachmentService submissionAttachmentService;
    
    @Transactional
    public SubmissionResponse createSubmission(UUID userId, String bearerToken, CreateSubmissionRequest request) {
        Assignment assignment = getAssignment(request.assignmentId());
        AssignmentGroupAvailability availability = validateSubmissionAllowed(assignment, userId);
        RemoteStoredFileResponse file = fileServiceClient.getMyFile(bearerToken, request.fileId());
        validateSubmissionFile(userId, assignment, file);
        fileServiceClient.markFileActive(bearerToken, request.fileId());
        
        Submission submission = submissionFactory.newSubmission(
                request.assignmentId(),
                userId,
                request.fileId()
        );
        Submission savedSubmission = submissionRepository.save(submission);
        submissionAttachmentService.createInitialAttachment(savedSubmission.getId(), userId, request.fileId());
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        boolean wasLate = savedSubmission.getSubmittedAt().isAfter(availability.getDeadline());
        assignmentEventPublisher.publishAssignmentSubmitted(new AssignmentSubmittedEventV1(
                UUID.randomUUID(),
                savedSubmission.getSubmittedAt(),
                savedSubmission.getId(),
                assignment.getId(),
                userId,
                subjectId,
                assignment.getTopicId(),
                savedSubmission.getSubmittedAt(),
                availability.getDeadline(),
                wasLate
        ));
        if (wasLate) {
            assignmentEventPublisher.publishDeadlineMissed(new DeadlineMissedEventV1(
                    UUID.randomUUID(),
                    savedSubmission.getSubmittedAt(),
                    userId,
                    DeadlineMissedEntityTypeV1.ASSIGNMENT,
                    assignment.getId(),
                    subjectId,
                    assignment.getTopicId(),
                    availability.getDeadline()
            ));
        }
        SubmissionResponse response = toResponse(savedSubmission, file);
        assignmentAuditService.record(userId, "SUBMISSION_CREATED", "SUBMISSION", response.id(), null, response);
        return response;
    }
    
    @Transactional(readOnly = true)
    public SubmissionPageResponse getSubmissionsByAssignment(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Assignment assignment = getAssignment(assignmentId);
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<Submission> submissionPage = submissionRepository.findAllByAssignmentId(
                assignmentId,
                pageRequest
        );
        
        return new SubmissionPageResponse(
                submissionPage.getContent().stream()
                        .map(submission -> toResponse(submission, null))
                        .toList(),
                submissionPage.getNumber(),
                submissionPage.getSize(),
                submissionPage.getTotalElements(),
                submissionPage.getTotalPages(),
                submissionPage.isFirst(),
                submissionPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getMySubmissionsByAssignment(UUID currentUserId, String bearerToken, UUID assignmentId) {
        Assignment assignment = getAssignment(assignmentId);
        resolveAvailability(assignment, currentUserId, Instant.now());
        return submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, currentUserId).stream()
                .map(submission -> toResponse(submission, fileServiceClient.getMyFile(bearerToken, submission.getFileId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getSubmission(
            UUID currentUserId,
            String bearerToken,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID submissionId
    ) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        Assignment assignment = getAssignment(submission.getAssignmentId());
        if (!privilegedAccess && !(teacherAccess && canManageAssignment(assignment, currentUserId)) && !submission.getUserId().equals(currentUserId)) {
            throw new AssignmentAccessDeniedException(assignment.getId(), currentUserId);
        }
        RemoteStoredFileResponse file = submission.getUserId().equals(currentUserId)
                ? fileServiceClient.getMyFile(bearerToken, submission.getFileId())
                : null;
        return toResponse(submission, file);
    }
    
    private Assignment getAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
    }
    
    private AssignmentGroupAvailability validateSubmissionAllowed(Assignment assignment, UUID userId) {
        if (assignment.getStatus() == AssignmentStatus.CLOSED) {
            throw new AssignmentClosedException(assignment.getId());
        }
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new AssignmentInvalidStateException(
                    assignment.getId(),
                    assignment.getStatus(),
                    "Submissions are allowed only for published assignments"
            );
        }
        Instant now = Instant.now();
        AssignmentGroupAvailability availability = resolveAvailability(assignment, userId, now);
        if (!availability.isAllowLateSubmissions() && now.isAfter(availability.getDeadline())) {
            throw new DeadlineExpiredException(assignment.getId(), availability.getDeadline());
        }

        long submissionCount = submissionRepository.countByAssignmentIdAndUserId(assignment.getId(), userId);
        if (!availability.isAllowResubmit() && submissionCount > 0) {
            throw new MaxSubmissionsExceededException(assignment.getId(), userId, availability.getMaxSubmissions());
        }
        if (submissionCount >= availability.getMaxSubmissions()) {
            throw new MaxSubmissionsExceededException(assignment.getId(), userId, availability.getMaxSubmissions());
        }
        return availability;
    }

    private AssignmentGroupAvailability resolveAvailability(Assignment assignment, UUID userId, Instant now) {
        Set<UUID> groupIds = educationServiceClient.getGroupsByUser(userId).stream()
                .map(groupMembership -> groupMembership.groupId())
                .collect(Collectors.toSet());
        if (groupIds.isEmpty()) {
            throw new AssignmentAccessDeniedException(assignment.getId(), userId);
        }
        List<AssignmentGroupAvailability> availabilityRows = assignmentGroupAvailabilityRepository
                .findAvailableForAssignmentsAndGroups(List.of(assignment.getId()), groupIds, now);
        return availabilityRows.stream()
                .findFirst()
                .orElseThrow(() -> new AssignmentAccessDeniedException(assignment.getId(), userId));
    }

    private void validateSubmissionFile(UUID userId, Assignment assignment, RemoteStoredFileResponse file) {
        if (!userId.equals(file.ownerId())) {
            throw new InvalidSubmissionFileException(file.id(), "Submission file belongs to a different user");
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

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "submittedAt";
        }
        return ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "submittedAt";
    }

    private Sort.Direction resolveSortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private void assertTeacherOwnership(Assignment assignment, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess || canManageAssignment(assignment, currentUserId)) {
            return;
        }
        throw new AssignmentAccessDeniedException(assignment.getId(), currentUserId);
    }

    private boolean canManageAssignment(Assignment assignment, UUID currentUserId) {
        if (assignment.getCreatedByUserId() != null && assignment.getCreatedByUserId().equals(currentUserId)) {
            return true;
        }
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        return educationServiceClient.getSubject(subjectId).teacherIds().contains(currentUserId);
    }

    private SubmissionResponse toResponse(Submission submission, RemoteStoredFileResponse file) {
        Grade grade = gradeRepository.findBySubmissionId(submission.getId()).orElse(null);
        return new SubmissionResponse(
                submission.getId(),
                submission.getAssignmentId(),
                submission.getUserId(),
                submission.getFileId(),
                file == null ? null : new SubmissionFileResponse(
                        file.id(),
                        file.originalFileName(),
                        file.contentType(),
                        file.sizeBytes(),
                        file.status()
                ),
                grade == null ? null : grade.getScore(),
                grade == null ? null : grade.getFeedback(),
                grade == null ? null : grade.getUpdatedAt(),
                grade != null,
                submission.getSubmittedAt(),
                submission.getUpdatedAt()
        );
    }
}
