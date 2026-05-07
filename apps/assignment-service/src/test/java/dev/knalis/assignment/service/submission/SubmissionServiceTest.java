package dev.knalis.assignment.service.submission;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.GroupMembershipResponse;
import dev.knalis.assignment.client.education.dto.TopicResponse;
import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.dto.request.CreateSubmissionRequest;
import dev.knalis.assignment.dto.response.SubmissionFileResponse;
import dev.knalis.assignment.dto.response.SubmissionPageResponse;
import dev.knalis.assignment.dto.response.SubmissionResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.exception.MaxSubmissionsExceededException;
import dev.knalis.assignment.exception.InvalidSubmissionFileException;
import dev.knalis.assignment.factory.submission.SubmissionFactory;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.common.AssignmentAuditService;
import dev.knalis.assignment.service.common.AssignmentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {
    
    @Mock
    private SubmissionRepository submissionRepository;
    
    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;
    
    @Mock
    private FileServiceClient fileServiceClient;

    @Mock
    private GradeRepository gradeRepository;
    
    @Mock
    private AssignmentEventPublisher assignmentEventPublisher;

    @Mock
    private AssignmentAuditService assignmentAuditService;
    
    @Mock
    private EducationServiceClient educationServiceClient;
    
    private SubmissionService submissionService;
    
    @BeforeEach
    void setUp() {
        submissionService = new SubmissionService(
                submissionRepository,
                assignmentRepository,
                assignmentGroupAvailabilityRepository,
                new SubmissionFactory(),
                fileServiceClient,
                gradeRepository,
                assignmentAuditService,
                assignmentEventPublisher,
                educationServiceClient
        );
    }
    
    @Test
    void createSubmissionMarksFileActive() {
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setMaxSubmissions(1);
        assignment.setAcceptedFileTypes(Set.of());
        assignment.setCreatedByUserId(userId);

        AssignmentGroupAvailability availability = new AssignmentGroupAvailability();
        availability.setId(UUID.randomUUID());
        availability.setAssignmentId(assignmentId);
        availability.setGroupId(groupId);
        availability.setVisible(true);
        availability.setDeadline(now.plusSeconds(3600));
        availability.setAllowLateSubmissions(false);
        availability.setMaxSubmissions(1);
        availability.setAllowResubmit(false);
        availability.setCreatedAt(now);
        availability.setUpdatedAt(now);
        
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setFileId(fileId);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);
        
        SubmissionResponse response = new SubmissionResponse(
                submissionId,
                assignmentId,
                userId,
                fileId,
                new SubmissionFileResponse(
                        fileId,
                        "submission.pdf",
                        "application/pdf",
                        1024L,
                        "UPLOADED"
                ),
                null,
                null,
                null,
                false,
                now,
                now
        );
        
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getGroupsByUser(userId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(assignmentGroupAvailabilityRepository.findAvailableForAssignmentsAndGroups(eq(List.of(assignmentId)), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(submissionRepository.countByAssignmentIdAndUserId(assignmentId, userId)).thenReturn(0L);
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Topic",
                1,
                now,
                now
        ));
        when(fileServiceClient.getMyFile("token", fileId)).thenReturn(new RemoteStoredFileResponse(
                fileId,
                userId,
                "submission.pdf",
                "application/pdf",
                1024L,
                "GENERIC",
                "PRIVATE",
                "UPLOADED",
                now.toString(),
                now.toString(),
                now.toString()
        ));
        when(submissionRepository.save(any(Submission.class))).thenReturn(submission);
        when(gradeRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());
        
        SubmissionResponse result = submissionService.createSubmission(
                userId,
                "token",
                new CreateSubmissionRequest(assignmentId, fileId)
        );
        
        verify(fileServiceClient).markFileActive("token", fileId);
        verify(assignmentEventPublisher).publishAssignmentSubmitted(any());
        assertEquals(response, result);
    }
    
    @Test
    void createSubmissionThrowsWhenFileBelongsToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setMaxSubmissions(1);
        assignment.setAcceptedFileTypes(Set.of());

        AssignmentGroupAvailability availability = new AssignmentGroupAvailability();
        availability.setId(UUID.randomUUID());
        availability.setAssignmentId(assignmentId);
        availability.setGroupId(groupId);
        availability.setVisible(true);
        availability.setDeadline(now.plusSeconds(3600));
        availability.setAllowLateSubmissions(false);
        availability.setMaxSubmissions(1);
        availability.setAllowResubmit(false);
        availability.setCreatedAt(now);
        availability.setUpdatedAt(now);
        
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getGroupsByUser(userId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(assignmentGroupAvailabilityRepository.findAvailableForAssignmentsAndGroups(eq(List.of(assignmentId)), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(submissionRepository.countByAssignmentIdAndUserId(assignmentId, userId)).thenReturn(0L);
        when(fileServiceClient.getMyFile("token", fileId)).thenReturn(new RemoteStoredFileResponse(
                fileId,
                UUID.randomUUID(),
                "submission.pdf",
                "application/pdf",
                1024L,
                "GENERIC",
                "PRIVATE",
                "UPLOADED",
                now.toString(),
                now.toString(),
                now.toString()
        ));
        
        assertThrows(
                InvalidSubmissionFileException.class,
                () -> submissionService.createSubmission(userId, "token", new CreateSubmissionRequest(assignmentId, fileId))
        );
    }
    
    @Test
    void getSubmissionsByAssignmentReturnsPageResponse() {
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setMaxSubmissions(1);
        assignment.setAcceptedFileTypes(Set.of());
        assignment.setCreatedByUserId(userId);
        
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setFileId(fileId);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);
        
        SubmissionResponse response = new SubmissionResponse(
                submissionId,
                assignmentId,
                userId,
                fileId,
                null,
                null,
                null,
                null,
                false,
                now,
                now
        );
        
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findAllByAssignmentId(eq(assignmentId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(submission)));
        when(gradeRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());
        
        SubmissionPageResponse result = submissionService.getSubmissionsByAssignment(
                userId,
                false,
                assignmentId,
                0,
                20,
                null,
                null
        );
        
        assertEquals(List.of(response), result.items());
        assertEquals(1L, result.totalElements());
    }

    @Test
    void createSubmissionThrowsWhenMaxSubmissionsReached() {
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();

        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setMaxSubmissions(1);

        AssignmentGroupAvailability availability = new AssignmentGroupAvailability();
        availability.setId(UUID.randomUUID());
        availability.setAssignmentId(assignmentId);
        availability.setGroupId(groupId);
        availability.setVisible(true);
        availability.setDeadline(now.plusSeconds(3600));
        availability.setAllowLateSubmissions(false);
        availability.setMaxSubmissions(1);
        availability.setAllowResubmit(false);
        availability.setCreatedAt(now);
        availability.setUpdatedAt(now);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getGroupsByUser(userId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(assignmentGroupAvailabilityRepository.findAvailableForAssignmentsAndGroups(eq(List.of(assignmentId)), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(submissionRepository.countByAssignmentIdAndUserId(assignmentId, userId)).thenReturn(1L);

        assertThrows(
                MaxSubmissionsExceededException.class,
                () -> submissionService.createSubmission(userId, "token", new CreateSubmissionRequest(assignmentId, fileId))
        );
    }
}
