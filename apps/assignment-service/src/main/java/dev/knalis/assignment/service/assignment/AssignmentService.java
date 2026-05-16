package dev.knalis.assignment.service.assignment;

import dev.knalis.assignment.dto.request.BulkUpsertAssignmentGroupAvailabilityRequest;
import dev.knalis.assignment.dto.request.CreateAssignmentRequest;
import dev.knalis.assignment.dto.request.MoveAssignmentRequest;
import dev.knalis.assignment.dto.request.UpdateAssignmentRequest;
import dev.knalis.assignment.dto.request.UpsertAssignmentGroupAvailabilityRequest;
import dev.knalis.assignment.dto.response.AssignmentGroupAvailabilityResponse;
import dev.knalis.assignment.dto.response.AssignmentPageResponse;
import dev.knalis.assignment.dto.response.AssignmentResponse;
import dev.knalis.assignment.dto.response.SearchItemResponse;
import dev.knalis.assignment.dto.response.SearchPageResponse;
import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.exception.AssignmentAlreadyArchivedException;
import dev.knalis.assignment.exception.AssignmentAccessDeniedException;
import dev.knalis.assignment.exception.AssignmentHasDependenciesException;
import dev.knalis.assignment.exception.AssignmentHasSubmissionsException;
import dev.knalis.assignment.exception.AssignmentNotArchivedException;
import dev.knalis.assignment.exception.AssignmentNotEditableException;
import dev.knalis.assignment.exception.AssignmentNotFoundException;
import dev.knalis.assignment.exception.AssignmentStateTransitionException;
import dev.knalis.assignment.factory.assignment.AssignmentFactory;
import dev.knalis.assignment.mapper.AssignmentMapper;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentAttachmentRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.common.AssignmentAuditService;
import dev.knalis.assignment.service.common.AssignmentEventPublisher;
import dev.knalis.assignment.service.common.SubmissionFileTypePolicy;
import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.AssignmentImportantChangeTypeV1;
import dev.knalis.contracts.event.AssignmentOpenedEventV1;
import dev.knalis.contracts.event.AssignmentUpdatedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "deadline",
            "createdAt",
            "updatedAt",
            "title",
            "orderIndex"
    );
    private static final Set<AssignmentStatus> STUDENT_VISIBLE_STATUSES = Set.of(
            AssignmentStatus.PUBLISHED,
            AssignmentStatus.CLOSED
    );
    
    private final AssignmentRepository assignmentRepository;
    private final AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;
    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentFactory assignmentFactory;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentAuditService assignmentAuditService;
    private final AssignmentEventPublisher assignmentEventPublisher;
    private final EducationServiceClient educationServiceClient;
    
    @Transactional
    public AssignmentResponse createAssignment(UUID currentUserId, boolean privilegedAccess, CreateAssignmentRequest request) {
        assertTeacherCanManageTopic(request.topicId(), currentUserId, privilegedAccess);
        Assignment assignment = assignmentFactory.newAssignment(
                request.topicId(),
                request.title(),
                request.description(),
                request.deadline(),
                AssignmentStatus.DRAFT,
                Boolean.TRUE.equals(request.allowLateSubmissions()),
                request.maxSubmissions() == null ? 1 : request.maxSubmissions(),
                Boolean.TRUE.equals(request.allowResubmit()),
                new LinkedHashSet<>(SubmissionFileTypePolicy.allowedContentTypes()),
                request.maxFileSizeMb(),
                request.maxPoints() == null ? 100 : request.maxPoints(),
                request.orderIndex() == null ? 0 : request.orderIndex()
        );
        assignment.setCreatedByUserId(currentUserId);
        AssignmentResponse response = assignmentMapper.toResponse(assignmentRepository.save(assignment));
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_CREATED", "ASSIGNMENT", response.id(), null, response);
        return response;
    }

    @Transactional
    public AssignmentResponse updateAssignment(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId,
            UpdateAssignmentRequest request
    ) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            throw new AssignmentNotEditableException(assignmentId, assignment.getStatus());
        }
        AssignmentResponse oldValue = assignmentMapper.toResponse(assignment);
        
        AssignmentImportantChangeTypeV1 importantChangeType = resolveImportantChangeType(
                assignment,
                request.title(),
                request.description(),
                request.deadline()
        );
        
        assignment.setTitle(request.title().trim());
        assignment.setDescription(request.description() == null || request.description().isBlank()
                ? null
                : request.description().trim());
        assignment.setDeadline(request.deadline());
        assignment.setAllowLateSubmissions(Boolean.TRUE.equals(request.allowLateSubmissions()));
        assignment.setMaxSubmissions(request.maxSubmissions() == null ? 1 : request.maxSubmissions());
        assignment.setAllowResubmit(Boolean.TRUE.equals(request.allowResubmit()));
        assignment.setAcceptedFileTypes(new LinkedHashSet<>(SubmissionFileTypePolicy.allowedContentTypes()));
        assignment.setMaxFileSizeMb(request.maxFileSizeMb());
        assignment.setMaxPoints(request.maxPoints() == null ? assignment.getMaxPoints() : request.maxPoints());
        
        Assignment savedAssignment = assignmentRepository.save(assignment);
        if (importantChangeType != null && savedAssignment.getStatus() == AssignmentStatus.PUBLISHED) {
            assignmentEventPublisher.publishAssignmentUpdated(new AssignmentUpdatedEventV1(
                    UUID.randomUUID(),
                    Instant.now(),
                    savedAssignment.getId(),
                    savedAssignment.getTopicId(),
                    savedAssignment.getTitle(),
                    savedAssignment.getDeadline(),
                    importantChangeType,
                    currentUserId
            ));
        }
        AssignmentResponse response = assignmentMapper.toResponse(savedAssignment);
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_UPDATED", "ASSIGNMENT", response.id(), oldValue, response);
        return response;
    }
    
    @Transactional(readOnly = true)
    public AssignmentPageResponse getAssignmentsByTopic(
            UUID topicId,
            UUID currentUserId,
            int page,
            int size,
            String sortBy,
            String direction,
            boolean privilegedAccess,
            boolean teacherAccess
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<Assignment> assignmentPage;
        if (privilegedAccess) {
            assignmentPage = assignmentRepository.findAllByTopicId(topicId, pageRequest);
        } else if (teacherAccess) {
            assignmentPage = canManageTopic(topicId, currentUserId, false)
                    ? assignmentRepository.findAllByTopicId(topicId, pageRequest)
                    : assignmentRepository.findVisibleByTopicIdForTeacher(
                            topicId,
                            currentUserId,
                            STUDENT_VISIBLE_STATUSES,
                            pageRequest
                    );
        } else {
            Set<UUID> groupIds = resolveStudentGroupIds(currentUserId);
            assignmentPage = groupIds.isEmpty()
                    ? Page.empty(pageRequest)
                    : assignmentRepository.findAvailableByTopicIdForGroups(
                            topicId,
                            STUDENT_VISIBLE_STATUSES,
                            groupIds,
                            Instant.now(),
                            pageRequest
                    );
        }
        
        return new AssignmentPageResponse(
                assignmentPage.getContent().stream().map(assignmentMapper::toResponse).toList(),
                assignmentPage.getNumber(),
                assignmentPage.getSize(),
                assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages(),
                assignmentPage.isFirst(),
                assignmentPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public SearchPageResponse searchAssignments(
            String query,
            int page,
            int size,
            String sortBy,
            String direction,
            boolean includeHidden
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        String normalizedQuery = query == null ? "" : query.trim();
        Page<Assignment> assignmentPage = includeHidden
                ? assignmentRepository.findAllByTitleContainingIgnoreCase(normalizedQuery, pageRequest)
                : assignmentRepository.findAllByTitleContainingIgnoreCaseAndStatus(
                        normalizedQuery,
                        AssignmentStatus.PUBLISHED,
                        pageRequest
                );

        return new SearchPageResponse(
                assignmentPage.getContent().stream().map(this::toSearchItem).toList(),
                assignmentPage.getNumber(),
                assignmentPage.getSize(),
                assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages(),
                assignmentPage.isFirst(),
                assignmentPage.isLast()
        );
    }
    
    @Transactional
    public AssignmentResponse getAssignment(
            UUID currentUserId,
            UUID assignmentId,
            boolean privilegedAccess,
            boolean teacherAccess
    ) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        ensureVisible(assignment, currentUserId, assignmentId, privilegedAccess, teacherAccess);
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            assignmentEventPublisher.publishAssignmentOpened(new AssignmentOpenedEventV1(
                    UUID.randomUUID(),
                    Instant.now(),
                    currentUserId,
                    assignment.getId(),
                    subjectId,
                    assignment.getTopicId()
            ));
        }
        return assignmentMapper.toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<AssignmentGroupAvailabilityResponse> getAssignmentAvailability(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId
    ) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        return assignmentGroupAvailabilityRepository.findAllByAssignmentIdOrderByCreatedAtAsc(assignmentId)
                .stream()
                .map(this::toAvailabilityResponse)
                .toList();
    }

    @Transactional
    public AssignmentGroupAvailabilityResponse upsertAssignmentAvailability(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId,
            UpsertAssignmentGroupAvailabilityRequest request
    ) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);

        AssignmentGroupAvailability availability = assignmentGroupAvailabilityRepository
                .findByAssignmentIdAndGroupId(assignmentId, request.groupId())
                .orElseGet(AssignmentGroupAvailability::new);
        availability.setAssignmentId(assignment.getId());
        availability.setGroupId(request.groupId());
        availability.setVisible(Boolean.TRUE.equals(request.visible()));
        availability.setAvailableFrom(request.availableFrom());
        availability.setDeadline(request.deadline());
        availability.setAllowLateSubmissions(Boolean.TRUE.equals(request.allowLateSubmissions()));
        availability.setMaxSubmissions(request.maxSubmissions() == null ? 1 : request.maxSubmissions());
        availability.setAllowResubmit(Boolean.TRUE.equals(request.allowResubmit()));

        AssignmentGroupAvailability savedAvailability = assignmentGroupAvailabilityRepository.save(availability);
        assignmentAuditService.record(
                currentUserId,
                "ASSIGNMENT_AVAILABILITY_UPDATED",
                "ASSIGNMENT",
                assignment.getId(),
                null,
                toAvailabilityResponse(savedAvailability)
        );
        return toAvailabilityResponse(savedAvailability);
    }

    @Transactional
    public List<AssignmentGroupAvailabilityResponse> bulkUpsertAssignmentAvailability(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId,
            BulkUpsertAssignmentGroupAvailabilityRequest request
    ) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);

        List<AssignmentGroupAvailabilityResponse> responses = request.items().stream()
                .map(item -> saveAssignmentAvailability(assignment, item))
                .map(this::toAvailabilityResponse)
                .toList();

        assignmentAuditService.record(
                currentUserId,
                "ASSIGNMENT_AVAILABILITY_BULK_UPDATED",
                "ASSIGNMENT",
                assignment.getId(),
                null,
                responses
        );
        return responses;
    }

    @Transactional
    public AssignmentResponse publishAssignment(UUID currentUserId, boolean privilegedAccess, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        AssignmentResponse oldValue = assignmentMapper.toResponse(assignment);
        if (assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new AssignmentStateTransitionException(assignmentId, assignment.getStatus(), AssignmentStatus.PUBLISHED);
        }

        assignment.setStatus(AssignmentStatus.PUBLISHED);
        Assignment savedAssignment = assignmentRepository.save(assignment);
        assignmentEventPublisher.publishAssignmentCreated(new AssignmentCreatedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                savedAssignment.getId(),
                savedAssignment.getTopicId(),
                savedAssignment.getTitle(),
                savedAssignment.getDeadline(),
                currentUserId
        ));
        AssignmentResponse response = assignmentMapper.toResponse(savedAssignment);
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_PUBLISHED", "ASSIGNMENT", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public AssignmentResponse closeAssignment(UUID currentUserId, boolean privilegedAccess, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        AssignmentResponse oldValue = assignmentMapper.toResponse(assignment);
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new AssignmentStateTransitionException(assignmentId, assignment.getStatus(), AssignmentStatus.CLOSED);
        }
        assignment.setStatus(AssignmentStatus.CLOSED);
        AssignmentResponse response = assignmentMapper.toResponse(assignmentRepository.save(assignment));
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_CLOSED", "ASSIGNMENT", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public AssignmentResponse reopenAssignment(UUID currentUserId, boolean privilegedAccess, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        AssignmentResponse oldValue = assignmentMapper.toResponse(assignment);
        if (assignment.getStatus() != AssignmentStatus.CLOSED) {
            throw new AssignmentStateTransitionException(assignmentId, assignment.getStatus(), AssignmentStatus.PUBLISHED);
        }
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        AssignmentResponse response = assignmentMapper.toResponse(assignmentRepository.save(assignment));
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_REOPENED", "ASSIGNMENT", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public AssignmentResponse archiveAssignment(UUID currentUserId, boolean privilegedAccess, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        AssignmentResponse oldValue = assignmentMapper.toResponse(assignment);
        if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            throw new AssignmentAlreadyArchivedException(assignmentId);
        }
        assignment.setStatus(AssignmentStatus.ARCHIVED);
        AssignmentResponse response = assignmentMapper.toResponse(assignmentRepository.save(assignment));
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_ARCHIVED", "ASSIGNMENT", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public AssignmentResponse restoreAssignment(UUID currentUserId, boolean privilegedAccess, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        AssignmentResponse oldValue = assignmentMapper.toResponse(assignment);
        if (assignment.getStatus() != AssignmentStatus.ARCHIVED) {
            throw new AssignmentNotArchivedException(assignmentId);
        }
        assignment.setStatus(AssignmentStatus.DRAFT);
        AssignmentResponse response = assignmentMapper.toResponse(assignmentRepository.save(assignment));
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_RESTORED", "ASSIGNMENT", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public AssignmentResponse moveAssignment(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID assignmentId,
            MoveAssignmentRequest request
    ) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        AssignmentResponse oldValue = assignmentMapper.toResponse(assignment);
        assignment.setTopicId(request.topicId());
        assignment.setOrderIndex(request.orderIndex());
        AssignmentResponse response = assignmentMapper.toResponse(assignmentRepository.save(assignment));
        assignmentAuditService.record(currentUserId, "ASSIGNMENT_MOVED", "ASSIGNMENT", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public void deleteAssignment(UUID currentUserId, boolean privilegedAccess, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AssignmentNotFoundException(assignmentId));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        if (assignment.getStatus() != AssignmentStatus.ARCHIVED) {
            throw new AssignmentHasDependenciesException(assignment.getId(), "ARCHIVE_REQUIRED");
        }
        long submissionsCount = submissionRepository.countByAssignmentId(assignmentId);
        if (submissionsCount > 0) {
            throw new AssignmentHasSubmissionsException(assignmentId, submissionsCount);
        }
        if (assignmentAttachmentRepository.existsByAssignmentId(assignmentId)) {
            throw new AssignmentHasDependenciesException(assignmentId, "ATTACHMENTS_PRESENT");
        }
        assignmentGroupAvailabilityRepository.deleteAll(assignmentGroupAvailabilityRepository.findAllByAssignmentIdOrderByCreatedAtAsc(assignmentId));
        assignmentRepository.delete(assignment);
    }
    
    private AssignmentImportantChangeTypeV1 resolveImportantChangeType(
            Assignment assignment,
            String newTitle,
            String newDescription,
            Instant newDeadline
    ) {
        String normalizedTitle = newTitle.trim();
        String normalizedDescription = newDescription == null || newDescription.isBlank()
                ? null
                : newDescription.trim();
        
        if (!assignment.getDeadline().equals(newDeadline)) {
            return AssignmentImportantChangeTypeV1.DEADLINE_CHANGED;
        }
        if (!assignment.getTitle().equals(normalizedTitle)) {
            return AssignmentImportantChangeTypeV1.TITLE_CHANGED;
        }
        if (!Objects.equals(assignment.getDescription(), normalizedDescription)) {
            return AssignmentImportantChangeTypeV1.DESCRIPTION_CHANGED;
        }
        return null;
    }

    private void ensureVisible(
            Assignment assignment,
            UUID currentUserId,
            UUID assignmentId,
            boolean privilegedAccess,
            boolean teacherAccess
    ) {
        if (STUDENT_VISIBLE_STATUSES.contains(assignment.getStatus())
                && isAvailableForStudent(assignment.getId(), currentUserId, Instant.now())) {
            return;
        }
        if (privilegedAccess) {
            return;
        }
        if (teacherAccess && canManageAssignment(assignment, currentUserId, false)) {
            return;
        }
        if (!teacherAccess) {
            throw new AssignmentNotFoundException(assignmentId);
        }
        throw new AssignmentAccessDeniedException(assignmentId, currentUserId);
    }

    private boolean isAvailableForStudent(UUID assignmentId, UUID currentUserId, Instant now) {
        Set<UUID> groupIds = resolveStudentGroupIds(currentUserId);
        return !groupIds.isEmpty()
                && assignmentGroupAvailabilityRepository.existsAvailableForGroups(assignmentId, groupIds, now);
    }

    private Set<UUID> resolveStudentGroupIds(UUID currentUserId) {
        return educationServiceClient.getGroupsByUser(currentUserId).stream()
                .map(groupMembership -> groupMembership.groupId())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private AssignmentGroupAvailability saveAssignmentAvailability(
            Assignment assignment,
            UpsertAssignmentGroupAvailabilityRequest request
    ) {
        AssignmentGroupAvailability availability = assignmentGroupAvailabilityRepository
                .findByAssignmentIdAndGroupId(assignment.getId(), request.groupId())
                .orElseGet(AssignmentGroupAvailability::new);
        availability.setAssignmentId(assignment.getId());
        availability.setGroupId(request.groupId());
        availability.setVisible(Boolean.TRUE.equals(request.visible()));
        availability.setAvailableFrom(request.availableFrom());
        availability.setDeadline(request.deadline());
        availability.setAllowLateSubmissions(Boolean.TRUE.equals(request.allowLateSubmissions()));
        availability.setMaxSubmissions(request.maxSubmissions() == null ? 1 : request.maxSubmissions());
        availability.setAllowResubmit(Boolean.TRUE.equals(request.allowResubmit()));
        return assignmentGroupAvailabilityRepository.save(availability);
    }

    private void assertTeacherOwnership(Assignment assignment, UUID currentUserId, boolean privilegedAccess) {
        if (canManageAssignment(assignment, currentUserId, privilegedAccess)) {
            return;
        }
        throw new AssignmentAccessDeniedException(assignment.getId(), currentUserId);
    }

    private void assertTeacherCanManageTopic(UUID topicId, UUID currentUserId, boolean privilegedAccess) {
        if (canManageTopic(topicId, currentUserId, privilegedAccess)) {
            return;
        }
        throw new AssignmentAccessDeniedException(topicId, currentUserId);
    }

    private boolean canManageAssignment(Assignment assignment, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return true;
        }
        if (assignment.getCreatedByUserId() != null && assignment.getCreatedByUserId().equals(currentUserId)) {
            return true;
        }
        return canManageTopic(assignment.getTopicId(), currentUserId, false);
    }

    private boolean canManageTopic(UUID topicId, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return true;
        }
        UUID subjectId = educationServiceClient.getTopic(topicId).subjectId();
        return isAssignedTeacherForSubject(subjectId, currentUserId);
    }

    private boolean isAssignedTeacherForSubject(UUID subjectId, UUID currentUserId) {
        return educationServiceClient.getSubject(subjectId).teacherIds().contains(currentUserId);
    }

    private SearchItemResponse toSearchItem(Assignment assignment) {
        return new SearchItemResponse(
                "ASSIGNMENT",
                assignment.getId(),
                assignment.getTitle(),
                "Deadline " + assignment.getDeadline(),
                Map.of(
                        "assignmentId", assignment.getId(),
                        "topicId", assignment.getTopicId(),
                        "status", assignment.getStatus().name(),
                        "deadline", assignment.getDeadline().toString()
                )
        );
    }

    private AssignmentGroupAvailabilityResponse toAvailabilityResponse(AssignmentGroupAvailability availability) {
        return new AssignmentGroupAvailabilityResponse(
                availability.getId(),
                availability.getAssignmentId(),
                availability.getGroupId(),
                availability.isVisible(),
                availability.getAvailableFrom(),
                availability.getDeadline(),
                availability.isAllowLateSubmissions(),
                availability.getMaxSubmissions(),
                availability.isAllowResubmit(),
                availability.getCreatedAt(),
                availability.getUpdatedAt()
        );
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "deadline";
        }
        return ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "deadline";
    }

    private Sort.Direction resolveSortDirection(String direction) {
        return "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
