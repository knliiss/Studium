package dev.knalis.assignment.service.attachment;

import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.GroupMembershipResponse;
import dev.knalis.assignment.client.education.dto.SubjectResponse;
import dev.knalis.assignment.client.education.dto.TopicResponse;
import dev.knalis.assignment.client.internal.FileServiceInternalClient;
import dev.knalis.assignment.dto.request.CreateSubmissionAttachmentRequest;
import dev.knalis.assignment.dto.response.SubmissionAttachmentResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.entity.Submission;
import dev.knalis.assignment.entity.SubmissionAttachment;
import dev.knalis.assignment.exception.AssignmentClosedException;
import dev.knalis.assignment.exception.SubmissionAttachmentNotFoundException;
import dev.knalis.assignment.exception.SubmissionFileAccessDeniedException;
import dev.knalis.assignment.exception.SubmissionNotAccessibleException;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import dev.knalis.assignment.repository.GradeRepository;
import dev.knalis.assignment.repository.SubmissionAttachmentRepository;
import dev.knalis.assignment.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionAttachmentServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;

    @Mock
    private SubmissionAttachmentRepository submissionAttachmentRepository;

    @Mock
    private GradeRepository gradeRepository;

    @Mock
    private EducationServiceClient educationServiceClient;

    @Mock
    private FileServiceClient fileServiceClient;

    @Mock
    private FileServiceInternalClient fileServiceInternalClient;

    private SubmissionAttachmentService submissionAttachmentService;

    @BeforeEach
    void setUp() {
        submissionAttachmentService = new SubmissionAttachmentService(
                submissionRepository,
                assignmentRepository,
                assignmentGroupAvailabilityRepository,
                submissionAttachmentRepository,
                gradeRepository,
                educationServiceClient,
                fileServiceClient,
                fileServiceInternalClient
        );
    }

    @Test
    void studentCanAttachFileToOwnSubmission() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, studentId, fileId);
        Assignment assignment = assignment(assignmentId, AssignmentStatus.PUBLISHED);
        AssignmentGroupAvailability availability = availability(assignmentId, groupId);
        SubmissionAttachment savedAttachment = new SubmissionAttachment();
        savedAttachment.setId(UUID.randomUUID());
        savedAttachment.setSubmissionId(submissionId);
        savedAttachment.setFileId(fileId);
        savedAttachment.setUploadedByUserId(studentId);
        savedAttachment.setCreatedAt(Instant.now());
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(gradeRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());
        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(assignmentGroupAvailabilityRepository.findAvailableForAssignmentsAndGroups(any(), any(), any())).thenReturn(List.of(availability));
        when(fileServiceClient.getMyFile("token", fileId)).thenReturn(file(fileId, studentId, "ATTACHMENT"));
        when(submissionAttachmentRepository.save(any(SubmissionAttachment.class))).thenReturn(savedAttachment);

        SubmissionAttachmentResponse response = submissionAttachmentService.addAttachment(
                studentId,
                "token",
                false,
                false,
                submissionId,
                new CreateSubmissionAttachmentRequest(fileId, "Solution")
        );

        assertEquals(fileId, response.fileId());
        verify(fileServiceClient).markFileActive("token", fileId);
    }

    @Test
    void studentCannotAttachFileToAnotherStudentsSubmission() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, ownerId, UUID.randomUUID());
        Assignment assignment = assignment(assignmentId, AssignmentStatus.PUBLISHED);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(
                SubmissionFileAccessDeniedException.class,
                () -> submissionAttachmentService.addAttachment(
                        studentId,
                        "token",
                        false,
                        false,
                        submissionId,
                        new CreateSubmissionAttachmentRequest(UUID.randomUUID(), null)
                )
        );
    }

    @Test
    void closedAssignmentBlocksSubmissionAttachmentUpload() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, studentId, UUID.randomUUID());
        Assignment assignment = assignment(assignmentId, AssignmentStatus.CLOSED);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(
                AssignmentClosedException.class,
                () -> submissionAttachmentService.addAttachment(
                        studentId,
                        "token",
                        false,
                        false,
                        submissionId,
                        new CreateSubmissionAttachmentRequest(fileId, null)
                )
        );
    }

    @Test
    void studentCanDownloadOwnSubmissionAttachment() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, studentId, fileId);
        Assignment assignment = assignment(assignmentId, AssignmentStatus.PUBLISHED);
        SubmissionAttachment attachment = new SubmissionAttachment();
        attachment.setId(attachmentId);
        attachment.setSubmissionId(submissionId);
        attachment.setFileId(fileId);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionAttachmentRepository.findByIdAndSubmissionId(attachmentId, submissionId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, false)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = submissionAttachmentService.downloadAttachment(
                studentId,
                false,
                false,
                submissionId,
                attachmentId,
                false
        );

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void otherStudentCannotDownloadSubmissionAttachment() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, ownerId, UUID.randomUUID());
        Assignment assignment = assignment(assignmentId, AssignmentStatus.PUBLISHED);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(
                SubmissionNotAccessibleException.class,
                () -> submissionAttachmentService.downloadAttachment(
                        studentId,
                        false,
                        false,
                        submissionId,
                        UUID.randomUUID(),
                        false
                )
        );
    }

    @Test
    void assignedTeacherCanDownloadSubmissionAttachment() {
        UUID teacherId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, UUID.randomUUID(), fileId);
        Assignment assignment = assignment(assignmentId, AssignmentStatus.PUBLISHED);
        assignment.setTopicId(topicId);
        SubmissionAttachment attachment = new SubmissionAttachment();
        attachment.setId(attachmentId);
        attachment.setSubmissionId(submissionId);
        attachment.setFileId(fileId);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(teacherId)));
        when(submissionAttachmentRepository.findByIdAndSubmissionId(attachmentId, submissionId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, true)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = submissionAttachmentService.downloadAttachment(
                teacherId,
                false,
                true,
                submissionId,
                attachmentId,
                true
        );

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void unrelatedTeacherIsDeniedForSubmissionAttachment() {
        UUID teacherId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, UUID.randomUUID(), UUID.randomUUID());
        Assignment assignment = assignment(assignmentId, AssignmentStatus.PUBLISHED);
        assignment.setTopicId(topicId);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(UUID.randomUUID())));

        assertThrows(
                SubmissionNotAccessibleException.class,
                () -> submissionAttachmentService.downloadAttachment(
                        teacherId,
                        false,
                        true,
                        submissionId,
                        UUID.randomUUID(),
                        false
                )
        );
    }

    @Test
    void adminCanDownloadSubmissionAttachment() {
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, UUID.randomUUID(), fileId);
        Assignment assignment = assignment(assignmentId, AssignmentStatus.ARCHIVED);
        SubmissionAttachment attachment = new SubmissionAttachment();
        attachment.setId(attachmentId);
        attachment.setSubmissionId(submissionId);
        attachment.setFileId(fileId);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionAttachmentRepository.findByIdAndSubmissionId(attachmentId, submissionId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, false)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = submissionAttachmentService.downloadAttachment(
                UUID.randomUUID(),
                true,
                false,
                submissionId,
                attachmentId,
                false
        );

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void attachmentFromAnotherSubmissionCannotBeDownloaded() {
        UUID studentId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Submission submission = submission(submissionId, assignmentId, studentId, UUID.randomUUID());
        Assignment assignment = assignment(assignmentId, AssignmentStatus.PUBLISHED);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionAttachmentRepository.findByIdAndSubmissionId(attachmentId, submissionId)).thenReturn(Optional.empty());

        assertThrows(
                SubmissionAttachmentNotFoundException.class,
                () -> submissionAttachmentService.downloadAttachment(
                        studentId,
                        false,
                        false,
                        submissionId,
                        attachmentId,
                        false
                )
        );
    }

    private Submission submission(UUID submissionId, UUID assignmentId, UUID userId, UUID fileId) {
        Submission submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignmentId(assignmentId);
        submission.setUserId(userId);
        submission.setFileId(fileId);
        submission.setSubmittedAt(Instant.now());
        submission.setUpdatedAt(Instant.now());
        return submission;
    }

    private Assignment assignment(UUID assignmentId, AssignmentStatus status) {
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(UUID.randomUUID());
        assignment.setTitle("Assignment");
        assignment.setStatus(status);
        assignment.setDeadline(Instant.now().plusSeconds(3600));
        return assignment;
    }

    private AssignmentGroupAvailability availability(UUID assignmentId, UUID groupId) {
        AssignmentGroupAvailability availability = new AssignmentGroupAvailability();
        availability.setId(UUID.randomUUID());
        availability.setAssignmentId(assignmentId);
        availability.setGroupId(groupId);
        availability.setVisible(true);
        availability.setDeadline(Instant.now().plusSeconds(3600));
        availability.setAllowLateSubmissions(true);
        availability.setMaxSubmissions(3);
        availability.setAllowResubmit(true);
        return availability;
    }

    private TopicResponse topic(UUID topicId, UUID subjectId) {
        Instant now = Instant.now();
        return new TopicResponse(topicId, subjectId, "Topic", 0, now, now);
    }

    private SubjectResponse subject(UUID subjectId, List<UUID> teacherIds) {
        Instant now = Instant.now();
        return new SubjectResponse(subjectId, "Subject", UUID.randomUUID(), List.of(), teacherIds, null, now, now);
    }

    private RemoteStoredFileResponse file(UUID fileId, UUID ownerId, String kind) {
        Instant now = Instant.now();
        return new RemoteStoredFileResponse(
                fileId,
                ownerId,
                "solution.pdf",
                "application/pdf",
                4096L,
                kind,
                "PRIVATE",
                "ACTIVE",
                now.toString(),
                now.toString(),
                now.toString()
        );
    }
}
