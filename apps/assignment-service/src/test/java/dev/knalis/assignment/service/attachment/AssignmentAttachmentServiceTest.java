package dev.knalis.assignment.service.attachment;

import dev.knalis.assignment.client.FileServiceClient;
import dev.knalis.assignment.client.dto.RemoteStoredFileResponse;
import dev.knalis.assignment.client.education.EducationServiceClient;
import dev.knalis.assignment.client.education.dto.GroupMembershipResponse;
import dev.knalis.assignment.client.education.dto.SubjectResponse;
import dev.knalis.assignment.client.education.dto.TopicResponse;
import dev.knalis.assignment.client.internal.FileServiceInternalClient;
import dev.knalis.assignment.dto.request.CreateAssignmentAttachmentRequest;
import dev.knalis.assignment.dto.response.AssignmentAttachmentResponse;
import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentAttachment;
import dev.knalis.assignment.entity.AssignmentStatus;
import dev.knalis.assignment.exception.AssignmentAttachmentNotFoundException;
import dev.knalis.assignment.exception.AssignmentFileAccessDeniedException;
import dev.knalis.assignment.exception.AssignmentNotAccessibleException;
import dev.knalis.assignment.exception.FileServiceUnavailableException;
import dev.knalis.assignment.repository.AssignmentAttachmentRepository;
import dev.knalis.assignment.repository.AssignmentGroupAvailabilityRepository;
import dev.knalis.assignment.repository.AssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentAttachmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentGroupAvailabilityRepository assignmentGroupAvailabilityRepository;

    @Mock
    private AssignmentAttachmentRepository assignmentAttachmentRepository;

    @Mock
    private EducationServiceClient educationServiceClient;

    @Mock
    private FileServiceClient fileServiceClient;

    @Mock
    private FileServiceInternalClient fileServiceInternalClient;

    private AssignmentAttachmentService assignmentAttachmentService;

    @BeforeEach
    void setUp() {
        assignmentAttachmentService = new AssignmentAttachmentService(
                assignmentRepository,
                assignmentGroupAvailabilityRepository,
                assignmentAttachmentRepository,
                educationServiceClient,
                fileServiceClient,
                fileServiceInternalClient
        );
    }

    @Test
    void teacherCanAttachFileToAssignment() {
        UUID teacherId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, teacherId, AssignmentStatus.PUBLISHED);
        AssignmentAttachment attachment = new AssignmentAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setAssignmentId(assignmentId);
        attachment.setFileId(fileId);
        attachment.setOriginalFileName("file.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(1024L);
        attachment.setUploadedByUserId(teacherId);
        attachment.setCreatedAt(Instant.now());
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(fileServiceClient.getMyFile("token", fileId)).thenReturn(file(fileId, teacherId, "ATTACHMENT"));
        when(assignmentAttachmentRepository.save(any(AssignmentAttachment.class))).thenReturn(attachment);

        AssignmentAttachmentResponse response = assignmentAttachmentService.addAttachment(
                teacherId,
                false,
                assignmentId,
                "token",
                new CreateAssignmentAttachmentRequest(fileId, "Task file")
        );

        assertEquals(fileId, response.fileId());
        assertEquals("file.pdf", response.originalFileName());
        verify(fileServiceClient).markFileActive("token", fileId);
    }

    @Test
    void studentCannotAttachTaskFile() {
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, creatorId, AssignmentStatus.PUBLISHED);
        assignment.setTopicId(topicId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(UUID.randomUUID())));

        assertThrows(
                AssignmentFileAccessDeniedException.class,
                () -> assignmentAttachmentService.addAttachment(
                        studentId,
                        false,
                        assignmentId,
                        "token",
                        new CreateAssignmentAttachmentRequest(UUID.randomUUID(), null)
                )
        );
    }

    @Test
    void studentCanReadPublishedAttachmentWhenAssignmentAccessible() {
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, UUID.randomUUID(), AssignmentStatus.PUBLISHED);
        AssignmentAttachment attachment = new AssignmentAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setAssignmentId(assignmentId);
        attachment.setFileId(fileId);
        attachment.setOriginalFileName("file.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(1024L);
        attachment.setUploadedByUserId(UUID.randomUUID());
        attachment.setCreatedAt(Instant.now());
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(assignmentGroupAvailabilityRepository.existsAvailableForGroups(eq(assignmentId), eq(Set.of(groupId)), any(Instant.class)))
                .thenReturn(true);
        when(assignmentAttachmentRepository.findAllByAssignmentIdOrderByCreatedAtAsc(assignmentId)).thenReturn(List.of(attachment));

        List<AssignmentAttachmentResponse> response = assignmentAttachmentService.listAttachments(
                studentId,
                false,
                false,
                assignmentId
        );

        assertEquals(1, response.size());
        assertEquals("file.pdf", response.get(0).originalFileName());
        verify(fileServiceInternalClient, never()).getMetadata(any());
    }

    @Test
    void studentCannotReadDraftAssignmentAttachment() {
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, UUID.randomUUID(), AssignmentStatus.DRAFT);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(
                AssignmentNotAccessibleException.class,
                () -> assignmentAttachmentService.listAttachments(studentId, false, false, assignmentId)
        );
        verify(assignmentAttachmentRepository, never()).findAllByAssignmentIdOrderByCreatedAtAsc(any());
        verify(fileServiceInternalClient, never()).getMetadata(any());
    }

    @Test
    void studentCannotReadArchivedAssignmentAttachment() {
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, UUID.randomUUID(), AssignmentStatus.ARCHIVED);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(
                AssignmentNotAccessibleException.class,
                () -> assignmentAttachmentService.listAttachments(studentId, false, false, assignmentId)
        );
        verify(assignmentAttachmentRepository, never()).findAllByAssignmentIdOrderByCreatedAtAsc(any());
        verify(fileServiceInternalClient, never()).getMetadata(any());
    }

    @Test
    void attachFailsWhenFileServiceUnavailable() {
        UUID teacherId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, teacherId, AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(fileServiceClient.getMyFile("token", fileId)).thenThrow(new FileServiceUnavailableException("metadata", fileId));

        assertThrows(
                FileServiceUnavailableException.class,
                () -> assignmentAttachmentService.addAttachment(
                        teacherId,
                        false,
                        assignmentId,
                        "token",
                        new CreateAssignmentAttachmentRequest(fileId, "Task file")
                )
        );
        verify(assignmentAttachmentRepository, never()).save(any());
        verify(fileServiceClient, never()).markFileActive(any(), any());
    }

    @Test
    void attachmentFromAnotherAssignmentCannotBeDownloaded() {
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, UUID.randomUUID(), AssignmentStatus.PUBLISHED);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getGroupsByUser(studentId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(assignmentGroupAvailabilityRepository.existsAvailableForGroups(eq(assignmentId), eq(Set.of(groupId)), any(Instant.class)))
                .thenReturn(true);
        when(assignmentAttachmentRepository.findByIdAndAssignmentId(attachmentId, assignmentId)).thenReturn(Optional.empty());

        assertThrows(
                AssignmentAttachmentNotFoundException.class,
                () -> assignmentAttachmentService.downloadAttachment(
                        studentId,
                        false,
                        false,
                        assignmentId,
                        attachmentId,
                        false
                )
        );
        verify(fileServiceInternalClient, never()).download(any(), anyBoolean());
    }

    @Test
    void assignedTeacherCanDownloadAttachment() {
        UUID teacherId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, UUID.randomUUID(), AssignmentStatus.DRAFT);
        assignment.setTopicId(topicId);
        AssignmentAttachment attachment = new AssignmentAttachment();
        attachment.setId(attachmentId);
        attachment.setAssignmentId(assignmentId);
        attachment.setFileId(fileId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(teacherId)));
        when(assignmentAttachmentRepository.findByIdAndAssignmentId(attachmentId, assignmentId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, true)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = assignmentAttachmentService.downloadAttachment(
                teacherId,
                false,
                true,
                assignmentId,
                attachmentId,
                true
        );

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void unrelatedTeacherIsDenied() {
        UUID teacherId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, UUID.randomUUID(), AssignmentStatus.PUBLISHED);
        assignment.setTopicId(topicId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(educationServiceClient.getTopic(topicId)).thenReturn(topic(topicId, subjectId));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(subject(subjectId, List.of(UUID.randomUUID())));

        assertThrows(
                AssignmentFileAccessDeniedException.class,
                () -> assignmentAttachmentService.downloadAttachment(
                        teacherId,
                        false,
                        true,
                        assignmentId,
                        UUID.randomUUID(),
                        false
                )
        );
    }

    @Test
    void adminCanDownloadAnyAssignmentAttachment() {
        UUID assignmentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Assignment assignment = assignment(assignmentId, UUID.randomUUID(), AssignmentStatus.ARCHIVED);
        AssignmentAttachment attachment = new AssignmentAttachment();
        attachment.setId(attachmentId);
        attachment.setAssignmentId(assignmentId);
        attachment.setFileId(fileId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentAttachmentRepository.findByIdAndAssignmentId(attachmentId, assignmentId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, false)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = assignmentAttachmentService.downloadAttachment(
                UUID.randomUUID(),
                true,
                false,
                assignmentId,
                attachmentId,
                false
        );

        assertEquals(200, response.getStatusCode().value());
    }

    private Assignment assignment(UUID assignmentId, UUID createdByUserId, AssignmentStatus status) {
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTopicId(UUID.randomUUID());
        assignment.setCreatedByUserId(createdByUserId);
        assignment.setTitle("Assignment");
        assignment.setDeadline(Instant.now().plusSeconds(3600));
        assignment.setStatus(status);
        return assignment;
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
                "file.pdf",
                "application/pdf",
                1024L,
                kind,
                "PRIVATE",
                "ACTIVE",
                now.toString(),
                now.toString(),
                now.toString()
        );
    }
}
