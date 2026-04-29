package dev.knalis.assignment.service.grade;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.TopicResponse;
import dev.knalis.assignment.dto.request.CreateGradeRequest;
import dev.knalis.assignment.dto.response.GradeResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.Grade;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.factory.grade.GradeFactory;
import dev.knalis.assignment.mapper.GradeMapper;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.common.AssignmentAuditService;
import dev.knalis.assignment.service.common.AssignmentEventPublisher;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {
    
    @Mock
    private GradeRepository gradeRepository;
    
    @Mock
    private SubmissionRepository submissionRepository;
    
    @Mock
    private AssignmentRepository assignmentRepository;
    
    @Mock
    private GradeMapper gradeMapper;
    
    @Mock
    private AssignmentEventPublisher assignmentEventPublisher;

    @Mock
    private AssignmentAuditService assignmentAuditService;
    
    @Mock
    private EducationServiceClient educationServiceClient;
    
    private GradeService gradeService;
    
    @BeforeEach
    void setUp() {
        gradeService = new GradeService(
                gradeRepository,
                submissionRepository,
                assignmentRepository,
                new GradeFactory(),
                gradeMapper,
                assignmentAuditService,
                assignmentEventPublisher,
                educationServiceClient
        );
    }
    
    @Test
    void createGradePublishesStudentTargetedEvent() {
        UUID actorId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignmentId(assignmentId);
        submission.setUserId(studentUserId);
        
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setTitle("Lab");
        assignment.setDeadline(now.plusSeconds(3600));
        
        Grade savedGrade = new Grade();
        savedGrade.setId(gradeId);
        savedGrade.setSubmissionId(submissionId);
        savedGrade.setScore(95);
        savedGrade.setFeedback("Well done");
        savedGrade.setCreatedAt(now);
        savedGrade.setUpdatedAt(now);
        
        GradeResponse response = new GradeResponse(gradeId, submissionId, 95, "Well done", now, now);
        
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(gradeRepository.existsBySubmissionId(submissionId)).thenReturn(false);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Topic",
                1,
                now,
                now
        ));
        when(gradeRepository.save(any(Grade.class))).thenReturn(savedGrade);
        when(gradeMapper.toResponse(savedGrade)).thenReturn(response);
        
        GradeResponse result = gradeService.createGrade(actorId, true, new CreateGradeRequest(submissionId, 95, "Well done"));
        
        assertEquals(response, result);
        ArgumentCaptor<GradeAssignedEventV1> captor = ArgumentCaptor.forClass(GradeAssignedEventV1.class);
        verify(assignmentEventPublisher).publishGradeAssigned(captor.capture());
        assertEquals(studentUserId, captor.getValue().studentUserId());
        assertEquals(assignmentId, captor.getValue().assignmentId());
        assertEquals(actorId, captor.getValue().assignedByUserId());
        assertEquals(subjectId, captor.getValue().subjectId());
        assertEquals(topicId, captor.getValue().topicId());
    }
}
