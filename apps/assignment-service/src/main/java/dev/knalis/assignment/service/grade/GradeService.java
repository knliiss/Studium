package dev.knalis.assignment.service.grade;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.dto.request.CreateGradeRequest;
import dev.knalis.assignment.dto.response.GradeResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.Grade;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.exception.AssignmentNotFoundException;
import dev.knalis.assignment.exception.AssignmentAccessDeniedException;
import dev.knalis.assignment.exception.AssignmentInvalidStateException;
import dev.knalis.assignment.exception.GradeAlreadyExistsException;
import dev.knalis.assignment.exception.SubmissionNotFoundException;
import dev.knalis.assignment.factory.grade.GradeFactory;
import dev.knalis.assignment.mapper.GradeMapper;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.common.AssignmentAuditService;
import dev.knalis.assignment.service.common.AssignmentEventPublisher;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GradeService {
    
    private final GradeRepository gradeRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final GradeFactory gradeFactory;
    private final GradeMapper gradeMapper;
    private final AssignmentAuditService assignmentAuditService;
    private final AssignmentEventPublisher assignmentEventPublisher;
    private final EducationServiceClient educationServiceClient;
    
    @Transactional
    public GradeResponse createGrade(UUID currentUserId, boolean privilegedAccess, CreateGradeRequest request) {
        Submission submission = submissionRepository.findById(request.submissionId())
                .orElseThrow(() -> new SubmissionNotFoundException(request.submissionId()));
        if (gradeRepository.existsBySubmissionId(request.submissionId())) {
            throw new GradeAlreadyExistsException(request.submissionId());
        }
        Assignment assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new AssignmentNotFoundException(submission.getAssignmentId()));
        assertTeacherOwnership(assignment, currentUserId, privilegedAccess);
        if (request.score() > assignment.getMaxPoints()) {
            throw new AssignmentInvalidStateException(
                    assignment.getId(),
                    assignment.getStatus(),
                    "Grade score cannot exceed assignment max points"
            );
        }
        UUID subjectId = educationServiceClient.getTopic(assignment.getTopicId()).subjectId();
        
        Grade grade = gradeFactory.newGrade(
                request.submissionId(),
                request.score(),
                request.feedback()
        );
        
        Grade savedGrade = gradeRepository.save(grade);
        assignmentEventPublisher.publishGradeAssigned(new GradeAssignedEventV1(
                UUID.randomUUID(),
                Instant.now(),
                savedGrade.getId(),
                savedGrade.getSubmissionId(),
                submission.getAssignmentId(),
                submission.getUserId(),
                savedGrade.getScore(),
                savedGrade.getFeedback(),
                currentUserId,
                subjectId,
                assignment.getTopicId()
        ));
        GradeResponse response = gradeMapper.toResponse(savedGrade);
        assignmentAuditService.record(currentUserId, "GRADE_ASSIGNED", "GRADE", response.id(), null, response);
        return response;
    }

    private void assertTeacherOwnership(Assignment assignment, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return;
        }
        if (assignment.getCreatedByUserId() != null && assignment.getCreatedByUserId().equals(currentUserId)) {
            return;
        }
        throw new AssignmentAccessDeniedException(assignment.getId(), currentUserId);
    }
}
