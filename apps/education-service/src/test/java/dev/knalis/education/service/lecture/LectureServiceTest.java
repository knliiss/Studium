package dev.knalis.education.service.lecture;

import dev.knalis.education.client.file.FileServiceClient;
import dev.knalis.education.client.file.dto.RemoteStoredFileResponse;
import dev.knalis.education.client.file.internal.FileServiceInternalClient;
import dev.knalis.education.dto.request.CreateLectureAttachmentRequest;
import dev.knalis.education.dto.request.CreateLectureRequest;
import dev.knalis.education.dto.request.UpdateLectureRequest;
import dev.knalis.education.dto.response.LectureAttachmentResponse;
import dev.knalis.education.dto.response.LectureResponse;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.Lecture;
import dev.knalis.education.entity.LectureAttachment;
import dev.knalis.education.entity.LectureStatus;
import dev.knalis.education.entity.Topic;
import dev.knalis.education.exception.EducationAccessDeniedException;
import dev.knalis.education.exception.LectureAttachmentNotFoundException;
import dev.knalis.education.exception.LectureHasDependenciesException;
import dev.knalis.education.exception.LectureNotAccessibleException;
import dev.knalis.education.exception.LectureNotEditableException;
import dev.knalis.education.factory.lecture.LectureFactory;
import dev.knalis.education.mapper.LectureMapper;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.LectureAttachmentRepository;
import dev.knalis.education.repository.LectureRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.repository.TopicRepository;
import dev.knalis.education.service.common.EducationAuditService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LectureServiceTest {

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private LectureAttachmentRepository lectureAttachmentRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SubjectTeacherRepository subjectTeacherRepository;

    @Mock
    private GroupStudentRepository groupStudentRepository;

    @Mock
    private LectureMapper lectureMapper;

    @Mock
    private EducationAuditService educationAuditService;

    @Mock
    private FileServiceClient fileServiceClient;

    @Mock
    private FileServiceInternalClient fileServiceInternalClient;

    private LectureService lectureService;

    @BeforeEach
    void setUp() {
        lectureService = new LectureService(
                lectureRepository,
                lectureAttachmentRepository,
                topicRepository,
                subjectRepository,
                subjectTeacherRepository,
                groupStudentRepository,
                new LectureFactory(),
                lectureMapper,
                educationAuditService,
                fileServiceClient,
                fileServiceInternalClient
        );

        lenient().when(lectureMapper.toResponse(any(Lecture.class))).thenAnswer(invocation -> {
            Lecture lecture = invocation.getArgument(0);
            return new LectureResponse(
                    lecture.getId(),
                    lecture.getSubjectId(),
                    lecture.getTopicId(),
                    lecture.getTitle(),
                    lecture.getContent(),
                    lecture.getStatus(),
                    lecture.getOrderIndex(),
                    lecture.getCreatedByUserId(),
                    lecture.getCreatedAt(),
                    lecture.getUpdatedAt()
            );
        });
        lenient().when(lectureRepository.save(any(Lecture.class))).thenAnswer(invocation -> {
            Lecture lecture = invocation.getArgument(0);
            if (lecture.getId() == null) {
                lecture.setId(UUID.randomUUID());
            }
            if (lecture.getCreatedAt() == null) {
                lecture.setCreatedAt(Instant.now());
            }
            lecture.setUpdatedAt(Instant.now());
            return lecture;
        });
    }

    @Test
    void assignedTeacherCanCreateLecture() {
        UUID userId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Topic topic = topic(topicId, subjectId);
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic));
        when(subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, userId)).thenReturn(true);

        LectureResponse response = lectureService.createLecture(
                userId,
                Set.of("ROLE_TEACHER"),
                subjectId,
                topicId,
                new CreateLectureRequest("  Intro  ", "  Basics  ", 1)
        );

        assertEquals("Intro", response.title());
        assertEquals("Basics", response.content());
        assertEquals(LectureStatus.DRAFT, response.status());
    }

    @Test
    void studentCannotCreateLecture() {
        UUID userId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Topic topic = topic(topicId, subjectId);
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(topic));

        assertThrows(
                EducationAccessDeniedException.class,
                () -> lectureService.createLecture(
                        userId,
                        Set.of("ROLE_STUDENT"),
                        subjectId,
                        topicId,
                        new CreateLectureRequest("Intro", null, 0)
                )
        );
    }

    @Test
    void lectureLifecycleTransitionsWork() {
        UUID userId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.DRAFT);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, userId)).thenReturn(true);

        LectureResponse published = lectureService.publishLecture(userId, Set.of("ROLE_TEACHER"), lectureId);
        LectureResponse closed = lectureService.closeLecture(userId, Set.of("ROLE_TEACHER"), lectureId);
        LectureResponse reopened = lectureService.reopenLecture(userId, Set.of("ROLE_TEACHER"), lectureId);
        LectureResponse archived = lectureService.archiveLecture(userId, Set.of("ROLE_TEACHER"), lectureId);
        LectureResponse restored = lectureService.restoreLecture(userId, Set.of("ROLE_TEACHER"), lectureId);

        assertEquals(LectureStatus.PUBLISHED, published.status());
        assertEquals(LectureStatus.CLOSED, closed.status());
        assertEquals(LectureStatus.PUBLISHED, reopened.status());
        assertEquals(LectureStatus.ARCHIVED, archived.status());
        assertEquals(LectureStatus.DRAFT, restored.status());
    }

    @Test
    void archivedLectureCannotBeEdited() {
        UUID userId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.ARCHIVED);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, userId)).thenReturn(true);

        assertThrows(
                LectureNotEditableException.class,
                () -> lectureService.updateLecture(
                        userId,
                        Set.of("ROLE_TEACHER"),
                        lectureId,
                        new UpdateLectureRequest("Updated", "Updated content", 0)
                )
        );
    }

    @Test
    void studentCanReadPublishedLectureInAccessibleSubject() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.PUBLISHED);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(true);

        LectureResponse response = lectureService.getLecture(studentId, Set.of("ROLE_STUDENT"), lectureId);

        assertEquals(lectureId, response.id());
    }

    @Test
    void studentCannotReadDraftLecture() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.DRAFT);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(true);

        assertThrows(
                LectureNotAccessibleException.class,
                () -> lectureService.getLecture(studentId, Set.of("ROLE_STUDENT"), lectureId)
        );
    }

    @Test
    void teacherCanAttachAndRemoveLectureFile() {
        UUID userId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.PUBLISHED);
        LectureAttachment attachment = new LectureAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setLectureId(lectureId);
        attachment.setFileId(fileId);
        attachment.setDisplayName("Lecture PDF");
        attachment.setUploadedByUserId(userId);
        attachment.setCreatedAt(Instant.now());

        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, userId)).thenReturn(true);
        when(fileServiceClient.getMyFile("token", fileId)).thenReturn(file(fileId, "ATTACHMENT"));
        when(lectureAttachmentRepository.save(any(LectureAttachment.class))).thenReturn(attachment);
        when(lectureAttachmentRepository.findByIdAndLectureId(attachment.getId(), lectureId)).thenReturn(Optional.of(attachment));

        LectureAttachmentResponse response = lectureService.addAttachment(
                userId,
                Set.of("ROLE_TEACHER"),
                "token",
                lectureId,
                new CreateLectureAttachmentRequest(fileId, "Lecture PDF")
        );
        lectureService.removeAttachment(userId, Set.of("ROLE_TEACHER"), lectureId, attachment.getId());

        assertEquals(fileId, response.fileId());
        verify(fileServiceClient).markFileActive("token", fileId);
        verify(lectureAttachmentRepository).delete(attachment);
    }

    @Test
    void studentCannotDownloadAttachmentFromDraftLecture() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.DRAFT);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(true);

        assertThrows(
                LectureNotAccessibleException.class,
                () -> lectureService.downloadAttachment(
                        studentId,
                        Set.of("ROLE_STUDENT"),
                        lectureId,
                        attachmentId,
                        false
                )
        );
        verify(lectureAttachmentRepository, never()).findByIdAndLectureId(any(), any());
        verify(fileServiceInternalClient, never()).download(any(), anyBoolean());
    }

    @Test
    void studentCannotDownloadAttachmentFromArchivedLecture() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.ARCHIVED);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(true);

        assertThrows(
                LectureNotAccessibleException.class,
                () -> lectureService.downloadAttachment(
                        studentId,
                        Set.of("ROLE_STUDENT"),
                        lectureId,
                        attachmentId,
                        false
                )
        );
        verify(lectureAttachmentRepository, never()).findByIdAndLectureId(any(), any());
        verify(fileServiceInternalClient, never()).download(any(), anyBoolean());
    }

    @Test
    void unrelatedStudentCannotDownloadAttachment() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.PUBLISHED);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(false);

        assertThrows(
                EducationAccessDeniedException.class,
                () -> lectureService.downloadAttachment(
                        studentId,
                        Set.of("ROLE_STUDENT"),
                        lectureId,
                        attachmentId,
                        false
                )
        );
        verify(lectureAttachmentRepository, never()).findByIdAndLectureId(any(), any());
        verify(fileServiceInternalClient, never()).download(any(), anyBoolean());
    }

    @Test
    void archivedLectureHardDeleteIsBlockedWhenAttachmentsExist() {
        UUID userId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.ARCHIVED);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, userId)).thenReturn(true);
        when(lectureAttachmentRepository.existsByLectureId(lectureId)).thenReturn(true);

        assertThrows(
                LectureHasDependenciesException.class,
                () -> lectureService.deleteLecture(userId, Set.of("ROLE_TEACHER"), lectureId)
        );
    }

    @Test
    void studentCanDownloadAttachmentWhenLectureAccessible() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.PUBLISHED);
        LectureAttachment attachment = new LectureAttachment();
        attachment.setId(attachmentId);
        attachment.setLectureId(lectureId);
        attachment.setFileId(fileId);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(true);
        when(lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, true)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = lectureService.downloadAttachment(
                studentId,
                Set.of("ROLE_STUDENT"),
                lectureId,
                attachmentId,
                true
        );

        assertEquals(200, response.getStatusCode().value());
        verify(fileServiceInternalClient).download(fileId, true);
    }

    @Test
    void studentCanDownloadAttachmentWhenLectureClosed() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.CLOSED);
        LectureAttachment attachment = new LectureAttachment();
        attachment.setId(attachmentId);
        attachment.setLectureId(lectureId);
        attachment.setFileId(fileId);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(true);
        when(lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, false)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = lectureService.downloadAttachment(
                studentId,
                Set.of("ROLE_STUDENT"),
                lectureId,
                attachmentId,
                false
        );

        assertEquals(200, response.getStatusCode().value());
        verify(fileServiceInternalClient).download(fileId, false);
    }

    @Test
    void assignedTeacherCanPreviewAttachment() {
        UUID teacherId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.DRAFT);
        LectureAttachment attachment = new LectureAttachment();
        attachment.setId(attachmentId);
        attachment.setLectureId(lectureId);
        attachment.setFileId(fileId);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, teacherId)).thenReturn(true);
        when(lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, true)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = lectureService.downloadAttachment(
                teacherId,
                Set.of("ROLE_TEACHER"),
                lectureId,
                attachmentId,
                true
        );

        assertEquals(200, response.getStatusCode().value());
        verify(fileServiceInternalClient).download(fileId, true);
    }

    @Test
    void adminCanDownloadAttachmentFromAnyLecture() {
        UUID adminId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.ARCHIVED);
        LectureAttachment attachment = new LectureAttachment();
        attachment.setId(attachmentId);
        attachment.setLectureId(lectureId);
        attachment.setFileId(fileId);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, false)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = lectureService.downloadAttachment(
                adminId,
                Set.of("ROLE_ADMIN"),
                lectureId,
                attachmentId,
                false
        );

        assertEquals(200, response.getStatusCode().value());
        verify(fileServiceInternalClient).download(fileId, false);
    }

    @Test
    void ownerCanPreviewAttachmentFromAnyLecture() {
        UUID ownerId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.ARCHIVED);
        LectureAttachment attachment = new LectureAttachment();
        attachment.setId(attachmentId);
        attachment.setLectureId(lectureId);
        attachment.setFileId(fileId);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)).thenReturn(Optional.of(attachment));
        when(fileServiceInternalClient.download(fileId, true)).thenReturn(ResponseEntity.ok(new byte[] {1}));

        ResponseEntity<byte[]> response = lectureService.downloadAttachment(
                ownerId,
                Set.of("ROLE_OWNER"),
                lectureId,
                attachmentId,
                true
        );

        assertEquals(200, response.getStatusCode().value());
        verify(fileServiceInternalClient).download(fileId, true);
    }

    @Test
    void attachmentFromAnotherLectureCannotBeDownloaded() {
        UUID studentId = UUID.randomUUID();
        UUID lectureId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Lecture lecture = lecture(lectureId, subjectId, LectureStatus.PUBLISHED);
        when(lectureRepository.findById(lectureId)).thenReturn(Optional.of(lecture));
        when(groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(studentId)).thenReturn(List.of(groupStudent(studentId, groupId)));
        when(subjectRepository.existsByIdAndBoundGroupIds(eq(subjectId), eq(List.of(groupId)))).thenReturn(true);
        when(lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)).thenReturn(Optional.empty());

        assertThrows(
                LectureAttachmentNotFoundException.class,
                () -> lectureService.downloadAttachment(
                        studentId,
                        Set.of("ROLE_STUDENT"),
                        lectureId,
                        attachmentId,
                        false
                )
        );
        verify(fileServiceInternalClient, never()).download(any(), anyBoolean());
    }

    private Topic topic(UUID topicId, UUID subjectId) {
        Topic topic = new Topic();
        topic.setId(topicId);
        topic.setSubjectId(subjectId);
        topic.setTitle("Topic");
        topic.setOrderIndex(0);
        topic.setCreatedAt(Instant.now());
        topic.setUpdatedAt(Instant.now());
        return topic;
    }

    private Lecture lecture(UUID lectureId, UUID subjectId, LectureStatus status) {
        Lecture lecture = new Lecture();
        lecture.setId(lectureId);
        lecture.setSubjectId(subjectId);
        lecture.setTopicId(UUID.randomUUID());
        lecture.setTitle("Lecture");
        lecture.setContent("Content");
        lecture.setStatus(status);
        lecture.setOrderIndex(0);
        lecture.setCreatedByUserId(UUID.randomUUID());
        lecture.setCreatedAt(Instant.now());
        lecture.setUpdatedAt(Instant.now());
        return lecture;
    }

    private GroupStudent groupStudent(UUID userId, UUID groupId) {
        GroupStudent membership = new GroupStudent();
        membership.setUserId(userId);
        membership.setGroupId(groupId);
        membership.setCreatedAt(Instant.now());
        membership.setUpdatedAt(Instant.now());
        return membership;
    }

    private RemoteStoredFileResponse file(UUID fileId, String kind) {
        return new RemoteStoredFileResponse(
                fileId,
                UUID.randomUUID(),
                "lecture.pdf",
                "application/pdf",
                128L,
                kind,
                "PRIVATE",
                "ACTIVE",
                true,
                Instant.now().toString(),
                Instant.now().toString(),
                Instant.now().toString()
        );
    }
}
