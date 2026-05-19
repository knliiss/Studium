package dev.knalis.assignment.service.submission;

import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.GroupMembershipResponse;
import dev.knalis.assignment.client.education.dto.SubjectResponse;
import dev.knalis.assignment.client.education.dto.TopicResponse;
import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.client.notification.NotificationServiceClient;
import dev.knalis.assignment.dto.request.CreateSubmissionRequest;
import dev.knalis.assignment.dto.request.ResubmitSubmissionRequest;
import dev.knalis.assignment.dto.request.ReturnSubmissionForRevisionRequest;
import dev.knalis.assignment.dto.response.SubmissionFileResponse;
import dev.knalis.assignment.dto.response.SubmissionPageResponse;
import dev.knalis.assignment.dto.response.SubmissionResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.entity.SubmissionStatus;
import dev.knalis.assignment.exception.AssignmentClosedException;
import dev.knalis.assignment.exception.MaxSubmissionsExceededException;
import dev.knalis.assignment.exception.InvalidSubmissionFileException;
import dev.knalis.assignment.factory.submission.SubmissionFactory;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionAttachmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import dev.knalis.assignment.service.attachment.SubmissionAttachmentService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Mock
    private SubmissionAttachmentService submissionAttachmentService;

    @Mock
    private SubmissionAttachmentRepository submissionAttachmentRepository;

    @Mock
    private NotificationServiceClient notificationServiceClient;
    
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
                educationServiceClient,
                notificationServiceClient,
                submissionAttachmentService,
                submissionAttachmentRepository
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
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setAttemptNumber(1);
        submission.setSubmittedLate(false);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getGroupsByUser(userId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(assignmentGroupAvailabilityRepository.findAvailableForAssignmentsAndGroups(eq(List.of(assignmentId)), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, userId)).thenReturn(List.of());
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
        verify(submissionAttachmentService).createInitialAttachment(eq(submissionId), eq(userId), any(RemoteStoredFileResponse.class));
        verify(assignmentEventPublisher).publishAssignmentSubmitted(any());
        assertEquals(submissionId, result.id());
        assertEquals("SUBMITTED", result.status());
        assertEquals(1, result.attemptNumber());
        assertEquals(1, result.maxAttempts());
        assertEquals(0, result.remainingAttempts());
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
        when(submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, userId)).thenReturn(List.of());
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
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setAttemptNumber(1);
        submission.setSubmittedLate(false);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);
        
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
        
        assertEquals(1, result.items().size());
        assertEquals(submissionId, result.items().getFirst().id());
        assertEquals("SUBMITTED", result.items().getFirst().status());
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
        Submission existingAttempt = new Submission();
        existingAttempt.setId(UUID.randomUUID());
        existingAttempt.setAssignmentId(assignmentId);
        existingAttempt.setUserId(userId);
        existingAttempt.setStatus(SubmissionStatus.SUBMITTED);
        existingAttempt.setAttemptNumber(1);
        existingAttempt.setSubmittedAt(now.minusSeconds(60));
        when(submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, userId))
                .thenReturn(List.of(existingAttempt));

        assertThrows(
                MaxSubmissionsExceededException.class,
                () -> submissionService.createSubmission(userId, "token", new CreateSubmissionRequest(assignmentId, fileId))
        );
    }

    @Test
    void createSubmissionThrowsWhenAssignmentClosed() {
        UUID userId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(UUID.randomUUID());
        assignment.setDeadline(Instant.now().plusSeconds(3600));
        assignment.setStatus(AssignmentStatus.CLOSED);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(
                AssignmentClosedException.class,
                () -> submissionService.createSubmission(userId, "token", new CreateSubmissionRequest(assignmentId, fileId))
        );
    }

    @Test
    void returnForRevisionMarksCurrentAttempt() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        Instant now = Instant.now();

        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setCreatedByUserId(teacherId);
        assignment.setStatus(AssignmentStatus.PUBLISHED);

        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignmentId(assignmentId);
        submission.setUserId(studentId);
        submission.setFileId(UUID.randomUUID());
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setAttemptNumber(1);
        submission.setSubmittedAt(now.minusSeconds(300));
        submission.setUpdatedAt(now.minusSeconds(300));

        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(submission));
        when(submissionRepository.save(submission)).thenReturn(submission);
        when(gradeRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());

        SubmissionResponse result = submissionService.returnForRevision(
                teacherId,
                false,
                submissionId,
                new ReturnSubmissionForRevisionRequest("Please update the report structure.", now.plusSeconds(3600))
        );

        assertEquals("RETURNED_FOR_REVISION", result.status());
        assertEquals("Please update the report structure.", result.reviewComment());
        assertNotNull(result.reviewedAt());
        assertNotNull(result.returnedForRevisionAt());
    }

    @Test
    void resubmitSubmissionCreatesNewAttemptWhenLatestWasReturnedForRevision() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID originalSubmissionId = UUID.randomUUID();
        UUID resubmittedSubmissionId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();

        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(topicId);
        assignment.setCreatedByUserId(teacherId);
        assignment.setDeadline(now.plusSeconds(3600));
        assignment.setStatus(AssignmentStatus.PUBLISHED);
        assignment.setMaxSubmissions(3);
        assignment.setAcceptedFileTypes(Set.of());

        AssignmentGroupAvailability availability = new AssignmentGroupAvailability();
        availability.setId(UUID.randomUUID());
        availability.setAssignmentId(assignmentId);
        availability.setGroupId(UUID.randomUUID());
        availability.setVisible(true);
        availability.setDeadline(now.plusSeconds(3600));
        availability.setAllowLateSubmissions(false);
        availability.setMaxSubmissions(3);
        availability.setAllowResubmit(true);
        availability.setCreatedAt(now);
        availability.setUpdatedAt(now);

        Submission latestAttempt = new Submission();
        latestAttempt.setId(originalSubmissionId);
        latestAttempt.setAssignmentId(assignmentId);
        latestAttempt.setUserId(studentId);
        latestAttempt.setFileId(UUID.randomUUID());
        latestAttempt.setStatus(SubmissionStatus.RETURNED_FOR_REVISION);
        latestAttempt.setAttemptNumber(1);
        latestAttempt.setRevisionDeadline(now.plusSeconds(1800));
        latestAttempt.setSubmittedAt(now.minusSeconds(600));
        latestAttempt.setUpdatedAt(now.minusSeconds(300));

        Submission resubmittedAttempt = new Submission();
        resubmittedAttempt.setId(resubmittedSubmissionId);
        resubmittedAttempt.setAssignmentId(assignmentId);
        resubmittedAttempt.setUserId(studentId);
        resubmittedAttempt.setFileId(fileId);
        resubmittedAttempt.setStatus(SubmissionStatus.RESUBMITTED);
        resubmittedAttempt.setAttemptNumber(2);
        resubmittedAttempt.setSubmittedLate(false);
        resubmittedAttempt.setSubmittedAt(now);
        resubmittedAttempt.setUpdatedAt(now);

        when(submissionRepository.findById(originalSubmissionId)).thenReturn(Optional.of(latestAttempt));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(availability.getGroupId())));
        when(assignmentGroupAvailabilityRepository.findAvailableForAssignmentsAndGroups(eq(List.of(assignmentId)), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, studentId))
                .thenReturn(List.of(latestAttempt));
        when(fileServiceClient.getMyFile("token", fileId)).thenReturn(new RemoteStoredFileResponse(
                fileId,
                studentId,
                "revision.pdf",
                "application/pdf",
                1024L,
                "GENERIC",
                "PRIVATE",
                "UPLOADED",
                now.toString(),
                now.toString(),
                now.toString()
        ));
        when(submissionRepository.save(any(Submission.class))).thenReturn(resubmittedAttempt);
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(topicId, subjectId, "Topic", 1, now, now));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(new SubjectResponse(
                subjectId,
                "Subject",
                null,
                List.of(),
                List.of(teacherId),
                null,
                now,
                now
        ));
        when(gradeRepository.findBySubmissionId(resubmittedSubmissionId)).thenReturn(Optional.empty());

        SubmissionResponse result = submissionService.resubmitSubmission(
                studentId,
                "token",
                originalSubmissionId,
                new ResubmitSubmissionRequest(fileId)
        );

        assertEquals("RESUBMITTED", result.status());
        assertEquals(2, result.attemptNumber());
        assertEquals(1, result.remainingAttempts());
        verify(notificationServiceClient).createAssignmentUpdateNotification(
                eq(teacherId),
                any(),
                any(),
                any()
        );
    }
}
