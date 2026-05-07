package dev.knalis.assignment.service.assignment;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.SubjectResponse;
import dev.knalis.assignment.client.education.dto.TopicResponse;
import dev.knalis.assignment.dto.request.CreateAssignmentRequest;
import dev.knalis.assignment.dto.request.UpdateAssignmentRequest;
import dev.knalis.assignment.dto.response.AssignmentPageResponse;
import dev.knalis.assignment.dto.response.AssignmentResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.factory.assignment.AssignmentFactory;
import dev.knalis.assignment.mapper.AssignmentMapper;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.common.AssignmentAuditService;
import dev.knalis.assignment.service.common.AssignmentEventPublisher;
import dev.knalis.contracts.event.AssignmentImportantChangeTypeV1;
import dev.knalis.contracts.event.AssignmentUpdatedEventV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {
    
    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;

    @Mock
    private SubmissionRepository submissionRepository;
    
    @Mock
    private AssignmentMapper assignmentMapper;
    
    @Mock
    private AssignmentEventPublisher assignmentEventPublisher;

    @Mock
    private AssignmentAuditService assignmentAuditService;
    
    @Mock
    private EducationServiceClient educationServiceClient;
    
    private AssignmentService assignmentService;
    
    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(
                assignmentRepository,
                assignmentGroupAvailabilityRepository,
                submissionRepository,
                new AssignmentFactory(),
                assignmentMapper,
                assignmentAuditService,
                assignmentEventPublisher,
                educationServiceClient
        );
    }
    
    @Test
    void createAssignmentSavesTrimmedFields() {
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant deadline = Instant.parse("2026-06-01T10:15:30Z");
        Instant now = Instant.now();
        
        Assignment savedAssignment = new Assignment();
        savedAssignment.setId(assignmentId);
        savedAssignment.setTopicId(topicId);
        savedAssignment.setTitle("Lab 1");
        savedAssignment.setDescription("Intro task");
        savedAssignment.setDeadline(deadline);
        savedAssignment.setStatus(AssignmentStatus.DRAFT);
        savedAssignment.setAllowLateSubmissions(false);
        savedAssignment.setMaxSubmissions(1);
        savedAssignment.setAllowResubmit(false);
        savedAssignment.setAcceptedFileTypes(Set.of());
        savedAssignment.setCreatedAt(now);
        savedAssignment.setUpdatedAt(now);
        
        AssignmentResponse response = new AssignmentResponse(
                assignmentId,
                topicId,
                "Lab 1",
                "Intro task",
                deadline,
                0,
                AssignmentStatus.DRAFT,
                false,
                1,
                false,
                Set.of(),
                null,
                100,
                now,
                now
        );
        
        when(assignmentRepository.save(any(Assignment.class))).thenReturn(savedAssignment);
        when(assignmentMapper.toResponse(savedAssignment)).thenReturn(response);
        
        AssignmentResponse result = assignmentService.createAssignment(
                UUID.randomUUID(),
                true,
                new CreateAssignmentRequest(topicId, "  Lab 1  ", "  Intro task  ", deadline, null, null, null, null, null, null, null)
        );
        
        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertEquals("Lab 1", captor.getValue().getTitle());
        assertEquals("Intro task", captor.getValue().getDescription());
        assertEquals(response, result);
    }
    
    @Test
    void getAssignmentsByTopicReturnsPageResponse() {
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant deadline = Instant.parse("2026-06-01T10:15:30Z");
        Instant now = Instant.now();
        
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setTitle("Lab 1");
        assignment.setDescription("Intro task");
        assignment.setDeadline(deadline);
        assignment.setOrderIndex(0);
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setAllowLateSubmissions(false);
        assignment.setMaxSubmissions(1);
        assignment.setAllowResubmit(false);
        assignment.setAcceptedFileTypes(Set.of());
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        
        AssignmentResponse response = new AssignmentResponse(
                assignmentId,
                topicId,
                "Lab 1",
                "Intro task",
                deadline,
                0,
                AssignmentStatus.PUBLISHED,
                false,
                1,
                false,
                Set.of(),
                null,
                100,
                now,
                now
        );
        
        when(assignmentRepository.findAllByTopicId(eq(topicId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(assignment)));
        when(assignmentMapper.toResponse(assignment)).thenReturn(response);
        
        AssignmentPageResponse result = assignmentService.getAssignmentsByTopic(
                topicId,
                UUID.randomUUID(),
                0,
                20,
                null,
                null,
                true,
                false
        );
        
        assertEquals(List.of(response), result.items());
        assertEquals(1L, result.totalElements());
    }

    @Test
    void getAssignmentsByTopicReturnsDraftsForAssignedTeacher() {
        UUID teacherId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Instant now = Instant.now();

        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setTitle("Lab 1");
        assignment.setDescription("Intro task");
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setOrderIndex(0);
        assignment.setStatus(AssignmentStatus.DRAFT);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);

        AssignmentResponse response = new AssignmentResponse(
                assignmentId,
                topicId,
                "Lab 1",
                "Intro task",
                now.plusSeconds(3600),
                0,
                AssignmentStatus.DRAFT,
                false,
                1,
                false,
                Set.of(),
                null,
                100,
                now,
                now
        );

        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Topic",
                0,
                now,
                now
        ));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(new SubjectResponse(
                subjectId,
                "Algorithms",
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                List.of(teacherId),
                "Course",
                now,
                now
        ));
        when(assignmentRepository.findAllByTopicId(eq(topicId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(assignment)));
        when(assignmentMapper.toResponse(assignment)).thenReturn(response);

        AssignmentPageResponse result = assignmentService.getAssignmentsByTopic(
                topicId,
                teacherId,
                0,
                20,
                null,
                null,
                false,
                true
        );

        assertEquals(List.of(response), result.items());
        verify(assignmentRepository).findAllByTopicId(eq(topicId), any(Pageable.class));
    }
    
    @Test
    void updateAssignmentPublishesImportantEventForDeadlineChange() {
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant deadline = Instant.parse("2026-06-01T10:15:30Z");
        Instant updatedDeadline = Instant.parse("2026-06-02T10:15:30Z");
        Instant now = Instant.now();
        
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setTitle("Lab 1");
        assignment.setDescription("Intro task");
        assignment.setDeadline(deadline);
        assignment.setOrderIndex(0);
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setAllowLateSubmissions(false);
        assignment.setMaxSubmissions(1);
        assignment.setAllowResubmit(false);
        assignment.setAcceptedFileTypes(Set.of());
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        
        AssignmentResponse response = new AssignmentResponse(
                assignmentId,
                topicId,
                "Lab 1",
                "Intro task",
                updatedDeadline,
                0,
                AssignmentStatus.PUBLISHED,
                false,
                1,
                false,
                Set.of(),
                null,
                100,
                now,
                now
        );
        
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);
        when(assignmentMapper.toResponse(assignment)).thenReturn(response);
        
        assignmentService.updateAssignment(
                UUID.randomUUID(),
                true,
                assignmentId,
                new UpdateAssignmentRequest(
                        "Lab 1",
                        "Intro task",
                        updatedDeadline,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        
        assertEquals(updatedDeadline, assignment.getDeadline());
        ArgumentCaptor<AssignmentUpdatedEventV1> captor = ArgumentCaptor.forClass(AssignmentUpdatedEventV1.class);
        verify(assignmentEventPublisher).publishAssignmentUpdated(captor.capture());
        assertEquals(AssignmentImportantChangeTypeV1.DEADLINE_CHANGED, captor.getValue().importantChangeType());
    }
    
    @Test
    void getAssignmentPublishesOpenedEvent() {
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setTitle("Lab 1");
        assignment.setDescription("Intro task");
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setOrderIndex(0);
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setAllowLateSubmissions(false);
        assignment.setMaxSubmissions(1);
        assignment.setAllowResubmit(false);
        assignment.setAcceptedFileTypes(Set.of());
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        
        AssignmentResponse response = new AssignmentResponse(
                assignmentId,
                topicId,
                "Lab 1",
                "Intro task",
                assignment.getDeadline(),
                0,
                AssignmentStatus.PUBLISHED,
                false,
                1,
                false,
                Set.of(),
                null,
                100,
                now,
                now
        );
        
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Topic",
                1,
                now,
                now
        ));
        when(assignmentMapper.toResponse(assignment)).thenReturn(response);
        
        AssignmentResponse result = assignmentService.getAssignment(userId, assignmentId, true, false);
        
        assertEquals(response, result);
        verify(assignmentEventPublisher).publishAssignmentOpened(any());
    }

    @Test
    void publishAssignmentPublishesCreatedEvent() {
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant now = Instant.now();

        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setTitle("Draft");
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setOrderIndex(0);
        assignment.setStatus(AssignmentStatus.DRAFT);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);
        when(assignmentMapper.toResponse(assignment)).thenReturn(new AssignmentResponse(
                assignmentId,
                topicId,
                "Draft",
                null,
                assignment.getDeadline(),
                0,
                AssignmentStatus.PUBLISHED,
                false,
                1,
                false,
                Set.of(),
                null,
                100,
                now,
                now
        ));

        assignmentService.publishAssignment(UUID.randomUUID(), true, assignmentId);

        assertEquals(AssignmentStatus.PUBLISHED, assignment.getStatus());
        verify(assignmentEventPublisher).publishAssignmentCreated(any());
    }
}
