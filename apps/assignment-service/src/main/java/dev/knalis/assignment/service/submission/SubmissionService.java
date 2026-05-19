package dev.knalis.assignment.service.submission;

import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.notification.NotificationServiceClient;
import dev.knalis.assignment.dto.request.CreateSubmissionRequest;
import dev.knalis.assignment.dto.request.ResubmitSubmissionRequest;
import dev.knalis.assignment.dto.request.ReturnSubmissionForRevisionRequest;
import dev.knalis.assignment.dto.response.SubmissionAttemptDetailResponse;
import dev.knalis.assignment.dto.response.SubmissionAttachmentResponse;
import dev.knalis.assignment.dto.response.SubmissionDetailResponse;
import dev.knalis.assignment.dto.response.SubmissionFileResponse;
import dev.knalis.assignment.dto.response.SubmissionPageResponse;
import dev.knalis.assignment.dto.response.SubmissionResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Grade;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.entity.SubmissionAttachment;
import dev.knalis.assignment.entity.SubmissionStatus;
import dev.knalis.assignment.exception.AssignmentAccessDeniedException;
import dev.knalis.assignment.exception.AssignmentClosedException;
import dev.knalis.assignment.exception.AssignmentInvalidStateException;
import dev.knalis.assignment.exception.AssignmentNotFoundException;
import dev.knalis.assignment.exception.DeadlineExpiredException;
import dev.knalis.assignment.exception.FileTooLargeException;
import dev.knalis.assignment.exception.FileTypeNotAllowedException;
import dev.knalis.assignment.exception.InvalidSubmissionFileException;
import dev.knalis.assignment.exception.MaxSubmissionsExceededException;
import dev.knalis.assignment.exception.ResubmissionNotAllowedException;
import dev.knalis.assignment.exception.SubmissionAttemptNotCurrentException;
import dev.knalis.assignment.exception.SubmissionNotFoundException;
import dev.knalis.assignment.factory.submission.SubmissionFactory;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionAttachmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.attachment.SubmissionAttachmentService;
import dev.knalis.assignment.service.common.AssignmentAuditService;
import dev.knalis.assignment.service.common.AssignmentEventPublisher;
import dev.knalis.assignment.service.common.SubmissionFileTypePolicy;
import dev.knalis.contracts.event.AssignmentSubmittedEventV1;
import dev.knalis.contracts.event.DeadlineMissedEntityTypeV1;
import dev.knalis.contracts.event.DeadlineMissedEventV1;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
    private final NotificationServiceClient notificationServiceClient;
    private final SubmissionAttachmentService submissionAttachmentService;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;

    @Transactional
    public SubmissionResponse createSubmission(UUID userId, String bearerToken, CreateSubmissionRequest request) {
        Assignment assignment = getAssignment(request.assignmentId());
        Instant now = Instant.now();
        AssignmentGroupAvailability availability = validateAssignmentOpenForSubmission(assignment, userId, now);
        List<Submission> attempts = submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignment.getId(), userId);
        Submission latestAttempt = latestAttempt(attempts);

        if (latestAttempt != null && latestAttempt.getStatus() == SubmissionStatus.RETURNED_FOR_REVISION) {
            return createAttempt(
                    assignment,
                    availability,
                    attempts,
                    latestAttempt,
                    userId,
                    bearerToken,
                    request.fileId(),
                    true
            );
        }

        if (latestAttempt != null && !availability.isAllowResubmit()) {
            throw new MaxSubmissionsExceededException(assignment.getId(), userId, availability.getMaxSubmissions());
        }

        return createAttempt(
                assignment,
                availability,
                attempts,
                latestAttempt,
                userId,
                bearerToken,
                request.fileId(),
                false
        );
    }

    @Transactional
    public SubmissionResponse resubmitSubmission(
            UUID currentUserId,
            String bearerToken,
            UUID submissionId,
            ResubmitSubmissionRequest request
    ) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (!submission.getUserId().equals(currentUserId)) {
            throw new AssignmentAccessDeniedException(submission.getAssignmentId(), currentUserId);
        }

        Assignment assignment = getAssignment(submission.getAssignmentId());
        Instant now = Instant.now();
        AssignmentGroupAvailability availability = validateAssignmentOpenForSubmission(assignment, currentUserId, now);
        List<Submission> attempts = submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(
                submission.getAssignmentId(),
                currentUserId
        );
        Submission latestAttempt = latestAttempt(attempts);
        if (latestAttempt == null || !latestAttempt.getId().equals(submissionId)) {
            throw new SubmissionAttemptNotCurrentException(submissionId);
        }
        if (latestAttempt.getStatus() != SubmissionStatus.RETURNED_FOR_REVISION) {
            throw new ResubmissionNotAllowedException(assignment.getId());
        }
        if (latestAttempt.getRevisionDeadline() != null && now.isAfter(latestAttempt.getRevisionDeadline())) {
            throw new DeadlineExpiredException(assignment.getId(), latestAttempt.getRevisionDeadline());
        }

        return createAttempt(
                assignment,
                availability,
                attempts,
                latestAttempt,
                currentUserId,
                bearerToken,
                request.fileId(),
                true
        );
    }

    @Transactional
    public SubmissionResponse returnForRevision(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID submissionId,
            ReturnSubmissionForRevisionRequest request
    ) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        Assignment assignment = getAssignment(submission.getAssignmentId());
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);

        List<Submission> attempts = submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(
                submission.getAssignmentId(),
                submission.getUserId()
        );
        Submission latestAttempt = latestAttempt(attempts);
        if (latestAttempt == null || !latestAttempt.getId().equals(submissionId)) {
            throw new SubmissionAttemptNotCurrentException(submissionId);
        }
        if (latestAttempt.getStatus() == SubmissionStatus.GRADED) {
            throw new ResubmissionNotAllowedException(assignment.getId());
        }

        Instant now = Instant.now();
        latestAttempt.setStatus(SubmissionStatus.RETURNED_FOR_REVISION);
        latestAttempt.setReviewComment(normalizeComment(request.feedback()));
        latestAttempt.setReviewedAt(now);
        latestAttempt.setReviewedByUserId(currentUserId);
        latestAttempt.setReturnedForRevisionAt(now);
        latestAttempt.setRevisionDeadline(request.revisionDeadline());
        Submission savedSubmission = submissionRepository.save(latestAttempt);
        SubmissionResponse response = toResponse(
                savedSubmission,
                null,
                assignment.getMaxSubmissions(),
                Math.max(assignment.getMaxSubmissions() - savedSubmission.getAttemptNumber(), 0)
        );
        assignmentAuditService.record(
                currentUserId,
                "SUBMISSION_RETURNED_FOR_REVISION",
                "SUBMISSION",
                response.id(),
                null,
                response
        );
        notifyStudentAboutRevision(assignment, savedSubmission);
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
        Page<Submission> submissionPage = submissionRepository.findAllByAssignmentId(assignmentId, pageRequest);

        return new SubmissionPageResponse(
                submissionPage.getContent().stream()
                        .map(submission -> toResponse(
                                submission,
                                null,
                                assignment.getMaxSubmissions(),
                                Math.max(assignment.getMaxSubmissions() - submission.getAttemptNumber(), 0)
                        ))
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
    public SubmissionPageResponse getReviewQueue(
            UUID currentUserId,
            boolean privilegedAccess,
            int page,
            int size,
            UUID assignmentId,
            String status
    ) {
        List<Assignment> visibleAssignments = loadManageableAssignments(currentUserId, privilegedAccess);
        if (assignmentId != null) {
            visibleAssignments = visibleAssignments.stream()
                    .filter(assignment -> assignment.getId().equals(assignmentId))
                    .toList();
        }
        if (visibleAssignments.isEmpty()) {
            return emptyPage(page, size);
        }

        Map<UUID, Assignment> assignmentsById = visibleAssignments.stream()
                .collect(Collectors.toMap(Assignment::getId, assignment -> assignment, (left, right) -> left, LinkedHashMap::new));
        List<Submission> latestAttempts = latestAttemptsByAssignmentAndStudent(
                submissionRepository.findAllByAssignmentIdInOrderBySubmittedAtDesc(assignmentsById.keySet())
        );

        Set<SubmissionStatus> allowedStatuses = normalizeStatusFilter(status);
        List<SubmissionResponse> items = latestAttempts.stream()
                .filter(submission -> allowedStatuses.isEmpty() || allowedStatuses.contains(submission.getStatus()))
                .sorted(Comparator.comparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(submission -> {
                    Assignment assignment = assignmentsById.get(submission.getAssignmentId());
                    int maxAttempts = assignment == null ? submission.getAttemptNumber() : assignment.getMaxSubmissions();
                    return toResponse(
                            submission,
                            null,
                            maxAttempts,
                            Math.max(maxAttempts - submission.getAttemptNumber(), 0)
                    );
                })
                .toList();

        return paginate(items, page, size);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getMySubmissionsByAssignment(UUID currentUserId, String bearerToken, UUID assignmentId) {
        Assignment assignment = getAssignment(assignmentId);
        AssignmentGroupAvailability availability = resolveAvailability(assignment, currentUserId, Instant.now());
        return submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, currentUserId).stream()
                .map(submission -> toResponse(
                        submission,
                        fileServiceClient.getMyFile(bearerToken, submission.getFileId()),
                        availability.getMaxSubmissions(),
                        Math.max(availability.getMaxSubmissions() - submission.getAttemptNumber(), 0)
                ))
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
        assertSubmissionAccess(submission, assignment, currentUserId, privilegedAccess, teacherAccess);
        RemoteStoredFileResponse file = submission.getUserId().equals(currentUserId)
                ? fileServiceClient.getMyFile(bearerToken, submission.getFileId())
                : null;
        return toResponse(
                submission,
                file,
                assignment.getMaxSubmissions(),
                Math.max(assignment.getMaxSubmissions() - submission.getAttemptNumber(), 0)
        );
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getSubmissionDetail(
            UUID currentUserId,
            String bearerToken,
            boolean privilegedAccess,
            boolean teacherAccess,
            UUID submissionId
    ) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        Assignment assignment = getAssignment(submission.getAssignmentId());
        assertSubmissionAccess(submission, assignment, currentUserId, privilegedAccess, teacherAccess);

        AssignmentGroupAvailability availability = resolveAvailability(assignment, submission.getUserId(), Instant.now());
        List<Submission> attempts = submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(
                submission.getAssignmentId(),
                submission.getUserId()
        );
        Submission currentAttempt = latestAttempt(attempts);
        Map<UUID, List<SubmissionAttachmentResponse>> attachmentsBySubmissionId = attachmentsBySubmissionId(
                attempts.stream().map(Submission::getId).toList()
        );

        List<SubmissionAttemptDetailResponse> attemptResponses = attempts.stream()
                .sorted(Comparator.comparing(Submission::getAttemptNumber).thenComparing(Submission::getSubmittedAt))
                .map(attempt -> toAttemptDetailResponse(
                        attempt,
                        currentUserId.equals(submission.getUserId())
                                ? fileServiceClient.getMyFile(bearerToken, attempt.getFileId())
                                : null,
                        availability.getMaxSubmissions(),
                        Math.max(availability.getMaxSubmissions() - attempt.getAttemptNumber(), 0),
                        attachmentsBySubmissionId.getOrDefault(attempt.getId(), List.of())
                ))
                .toList();

        SubmissionAttemptDetailResponse currentAttemptResponse = attemptResponses.stream()
                .filter(attempt -> currentAttempt != null && attempt.id().equals(currentAttempt.getId()))
                .findFirst()
                .orElse(null);

        Integer finalScore = currentAttemptResponse == null || currentAttemptResponse.score() == null
                ? null
                : currentAttemptResponse.score();
        String latestTeacherComment = currentAttemptResponse == null
                ? null
                : firstNonBlank(currentAttemptResponse.reviewComment(), currentAttemptResponse.feedback());

        return new SubmissionDetailResponse(
                assignment.getId(),
                submission.getUserId(),
                currentAttempt == null ? SubmissionStatus.SUBMITTED.name() : currentAttempt.getStatus().name(),
                currentAttempt == null ? 1 : currentAttempt.getAttemptNumber(),
                availability.getMaxSubmissions(),
                currentAttempt == null
                        ? availability.getMaxSubmissions()
                        : Math.max(availability.getMaxSubmissions() - currentAttempt.getAttemptNumber(), 0),
                currentAttempt == null ? null : currentAttempt.getRevisionDeadline(),
                latestTeacherComment,
                finalScore,
                assignment.getMaxPoints(),
                currentAttemptResponse,
                attemptResponses
        );
    }

    private SubmissionResponse createAttempt(
            Assignment assignment,
            AssignmentGroupAvailability availability,
            List<Submission> previousAttempts,
            Submission latestAttempt,
            UUID userId,
            String bearerToken,
            UUID fileId,
            boolean resubmission
    ) {
        validateAttemptWorkflow(assignment, availability, previousAttempts, latestAttempt, userId, resubmission);

        RemoteStoredFileResponse file = fileServiceClient.getMyFile(bearerToken, fileId);
        validateSubmissionFile(userId, assignment, file);
        fileServiceClient.markFileActive(bearerToken, fileId);

        int nextAttemptNumber = previousAttempts.size() + 1;
        Instant effectiveDeadline = latestAttempt != null && latestAttempt.getRevisionDeadline() != null
                ? latestAttempt.getRevisionDeadline()
                : availability.getDeadline();
        boolean submittedLate = effectiveDeadline != null && Instant.now().isAfter(effectiveDeadline);
        Submission submission = submissionFactory.newSubmission(
                assignment.getId(),
                userId,
                fileId,
                resubmission ? SubmissionStatus.RESUBMITTED : SubmissionStatus.SUBMITTED,
                nextAttemptNumber,
                submittedLate
        );
        Submission savedSubmission = submissionRepository.save(submission);
        submissionAttachmentService.createInitialAttachment(savedSubmission.getId(), userId, file);
        publishSubmissionEvents(assignment, savedSubmission, effectiveDeadline);

        SubmissionResponse response = toResponse(
                savedSubmission,
                file,
                availability.getMaxSubmissions(),
                Math.max(availability.getMaxSubmissions() - savedSubmission.getAttemptNumber(), 0)
        );
        assignmentAuditService.record(
                userId,
                resubmission ? "SUBMISSION_RESUBMITTED" : "SUBMISSION_CREATED",
                "SUBMISSION",
                response.id(),
                null,
                response
        );
        if (resubmission) {
            notifyTeachersAboutResubmission(assignment, savedSubmission);
        }
        return response;
    }

    private void validateAttemptWorkflow(
            Assignment assignment,
            AssignmentGroupAvailability availability,
            List<Submission> previousAttempts,
            Submission latestAttempt,
            UUID userId,
            boolean resubmission
    ) {
        if (previousAttempts.size() >= availability.getMaxSubmissions()) {
            throw new MaxSubmissionsExceededException(assignment.getId(), userId, availability.getMaxSubmissions());
        }
        if (latestAttempt == null) {
            return;
        }
        if (latestAttempt.getStatus() == SubmissionStatus.RETURNED_FOR_REVISION) {
            if (latestAttempt.getRevisionDeadline() != null && Instant.now().isAfter(latestAttempt.getRevisionDeadline())) {
                throw new DeadlineExpiredException(assignment.getId(), latestAttempt.getRevisionDeadline());
            }
            return;
        }
        if (resubmission) {
            throw new ResubmissionNotAllowedException(assignment.getId());
        }
        if (latestAttempt.getStatus() == SubmissionStatus.GRADED && availability.isAllowResubmit()) {
            return;
        }
        throw new ResubmissionNotAllowedException(assignment.getId());
    }

    private void publishSubmissionEvents(Assignment assignment, Submission submission, Instant effectiveDeadline) {
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        assignmentEventPublisher.publishAssignmentSubmitted(new AssignmentSubmittedEventV1(
                UUID.randomUUID(),
                submission.getSubmittedAt(),
                submission.getId(),
                assignment.getId(),
                submission.getUserId(),
                subjectId,
                assignment.getTopicId(),
                submission.getSubmittedAt(),
                effectiveDeadline,
                submission.isSubmittedLate()
        ));
        if (submission.isSubmittedLate()) {
            assignmentEventPublisher.publishDeadlineMissed(new DeadlineMissedEventV1(
                    UUID.randomUUID(),
                    submission.getSubmittedAt(),
                    submission.getUserId(),
                    DeadlineMissedEntityTypeV1.ASSIGNMENT,
                    assignment.getId(),
                    subjectId,
                    assignment.getTopicId(),
                    effectiveDeadline
            ));
        }
    }

    private Assignment getAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
    }

    private AssignmentGroupAvailability validateAssignmentOpenForSubmission(Assignment assignment, UUID userId, Instant now) {
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
        AssignmentGroupAvailability availability = resolveAvailability(assignment, userId, now);
        if (!availability.isAllowLateSubmissions() && now.isAfter(availability.getDeadline())) {
            throw new DeadlineExpiredException(assignment.getId(), availability.getDeadline());
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
        if (!SubmissionFileTypePolicy.isAllowed(file.contentType())) {
            throw new FileTypeNotAllowedException(
                    assignment.getId(),
                    file.id(),
                    file.contentType(),
                    SubmissionFileTypePolicy.allowedContentTypeSet()
            );
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

    private void assertSubmissionAccess(
            Submission submission,
            Assignment assignment,
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherAccess
    ) {
        if (privilegedAccess) {
            return;
        }
        if (teacherAccess && canManageAssignment(assignment, currentUserId)) {
            return;
        }
        if (submission.getUserId().equals(currentUserId)) {
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

    private List<Assignment> loadManageableAssignments(UUID currentUserId, boolean privilegedAccess) {
        List<Assignment> assignments = assignmentRepository.findAll().stream()
                .filter(assignment -> assignment.getStatus() != AssignmentStatus.ARCHIVED)
                .toList();
        if (privilegedAccess) {
            return assignments;
        }
        return assignments.stream()
                .filter(assignment -> canManageAssignment(assignment, currentUserId))
                .toList();
    }

    private Set<SubmissionStatus> normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return Set.of();
        }
        try {
            return Set.of(SubmissionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Set.of();
        }
    }

    private List<Submission> latestAttemptsByAssignmentAndStudent(Collection<Submission> attempts) {
        Map<String, Submission> latestByCompositeKey = new LinkedHashMap<>();
        for (Submission attempt : attempts) {
            String compositeKey = attempt.getAssignmentId() + ":" + attempt.getUserId();
            Submission current = latestByCompositeKey.get(compositeKey);
            if (current == null || compareAttempts(attempt, current) > 0) {
                latestByCompositeKey.put(compositeKey, attempt);
            }
        }
        return new ArrayList<>(latestByCompositeKey.values());
    }

    private Submission latestAttempt(List<Submission> attempts) {
        return attempts.stream()
                .max(this::compareAttempts)
                .orElse(null);
    }

    private int compareAttempts(Submission left, Submission right) {
        return Comparator.comparing(Submission::getAttemptNumber)
                .thenComparing(Submission::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(left, right);
    }

    private SubmissionPageResponse paginate(List<SubmissionResponse> items, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int fromIndex = Math.min(safePage * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        List<SubmissionResponse> pageItems = items.subList(fromIndex, toIndex);
        int totalPages = items.isEmpty() ? 0 : (int) Math.ceil(items.size() / (double) safeSize);
        return new SubmissionPageResponse(
                pageItems,
                safePage,
                safeSize,
                items.size(),
                totalPages,
                safePage == 0,
                safePage >= Math.max(totalPages - 1, 0)
        );
    }

    private SubmissionPageResponse emptyPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return new SubmissionPageResponse(List.of(), safePage, safeSize, 0, 0, true, true);
    }

    private SubmissionResponse toResponse(
            Submission submission,
            RemoteStoredFileResponse file,
            int maxAttempts,
            int remainingAttempts
    ) {
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
                grade != null || submission.getReviewedAt() != null,
                submission.getStatus().name(),
                submission.getAttemptNumber(),
                maxAttempts,
                remainingAttempts,
                submission.isSubmittedLate(),
                submission.getReviewComment(),
                submission.getReviewedAt(),
                submission.getReviewedByUserId(),
                submission.getReturnedForRevisionAt(),
                submission.getRevisionDeadline(),
                submission.getSubmittedAt(),
                submission.getUpdatedAt()
        );
    }

    private SubmissionAttemptDetailResponse toAttemptDetailResponse(
            Submission submission,
            RemoteStoredFileResponse file,
            int maxAttempts,
            int remainingAttempts,
            List<SubmissionAttachmentResponse> attachments
    ) {
        SubmissionResponse response = toResponse(submission, file, maxAttempts, remainingAttempts);
        return new SubmissionAttemptDetailResponse(
                response.id(),
                response.assignmentId(),
                response.userId(),
                response.fileId(),
                response.file(),
                response.score(),
                response.feedback(),
                response.gradedAt(),
                response.reviewed(),
                response.status(),
                response.attemptNumber(),
                response.maxAttempts(),
                response.remainingAttempts(),
                response.submittedLate(),
                response.reviewComment(),
                response.reviewedAt(),
                response.reviewedByUserId(),
                response.returnedForRevisionAt(),
                response.revisionDeadline(),
                response.submittedAt(),
                response.updatedAt(),
                attachments
        );
    }

    private Map<UUID, List<SubmissionAttachmentResponse>> attachmentsBySubmissionId(Collection<UUID> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        return submissionAttachmentRepository.findAll().stream()
                .filter(attachment -> submissionIds.contains(attachment.getSubmissionId()))
                .map(this::toAttachmentResponse)
                .collect(Collectors.groupingBy(
                        SubmissionAttachmentResponse::submissionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private SubmissionAttachmentResponse toAttachmentResponse(SubmissionAttachment attachment) {
        return new SubmissionAttachmentResponse(
                attachment.getId(),
                attachment.getSubmissionId(),
                attachment.getFileId(),
                attachment.getDisplayName(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                isPreviewAvailable(attachment.getContentType()),
                attachment.getUploadedByUserId(),
                attachment.getCreatedAt()
        );
    }

    private boolean isPreviewAvailable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/pdf") || normalized.startsWith("image/");
    }

    private String normalizeComment(String feedback) {
        return feedback == null || feedback.isBlank() ? null : feedback.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    private void notifyStudentAboutRevision(Assignment assignment, Submission submission) {
        try {
            notificationServiceClient.createAssignmentUpdateNotification(
                    submission.getUserId(),
                    "Assignment returned for revision",
                    "Your submission was returned for revision. Open the assignment to review feedback and submit a new version.",
                    Map.of(
                            "assignmentId", assignment.getId(),
                            "submissionId", submission.getId(),
                            "revisionDeadline", submission.getRevisionDeadline()
                    )
            );
        } catch (IllegalStateException exception) {
            log.warn("Failed to notify student about revision assignmentId={} submissionId={} userId={}",
                    assignment.getId(),
                    submission.getId(),
                    submission.getUserId(),
                    exception);
        }
    }

    private void notifyTeachersAboutResubmission(Assignment assignment, Submission submission) {
        Set<UUID> teacherIds = new LinkedHashSet<>();
        if (assignment.getCreatedByUserId() != null) {
            teacherIds.add(assignment.getCreatedByUserId());
        }
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        teacherIds.addAll(educationServiceClient.getSubject(subjectId).teacherIds());
        teacherIds.remove(submission.getUserId());
        for (UUID teacherId : teacherIds) {
            try {
                notificationServiceClient.createAssignmentUpdateNotification(
                        teacherId,
                        "Assignment resubmitted",
                        "A student submitted a new revision for review.",
                        Map.of(
                                "assignmentId", assignment.getId(),
                                "submissionId", submission.getId(),
                                "studentId", submission.getUserId()
                        )
                );
            } catch (IllegalStateException exception) {
                log.warn("Failed to notify teacher about resubmission assignmentId={} submissionId={} teacherId={}",
                        assignment.getId(),
                        submission.getId(),
                        teacherId,
                        exception);
            }
        }
    }
}
