package dev.knalis.education.service.lecture;

import dev.knalis.education.client.file.FileServiceClient;
import dev.knalis.education.client.file.dto.RemoteStoredFileResponse;
import dev.knalis.education.client.file.internal.FileServiceInternalClient;
import dev.knalis.education.dto.request.CreateLectureAttachmentRequest;
import dev.knalis.education.dto.request.CreateLectureRequest;
import dev.knalis.education.dto.request.UpdateLectureRequest;
import dev.knalis.education.dto.response.LectureAttachmentResponse;
import dev.knalis.education.dto.response.LecturePageResponse;
import dev.knalis.education.dto.response.LectureResponse;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.Lecture;
import dev.knalis.education.entity.LectureAttachment;
import dev.knalis.education.entity.LectureStatus;
import dev.knalis.education.entity.Topic;
import dev.knalis.education.exception.EducationAccessDeniedException;
import dev.knalis.education.exception.FileAttachmentNotAllowedException;
import dev.knalis.education.exception.LectureAlreadyArchivedException;
import dev.knalis.education.exception.LectureAttachmentNotFoundException;
import dev.knalis.education.exception.LectureHasDependenciesException;
import dev.knalis.education.exception.LectureNotAccessibleException;
import dev.knalis.education.exception.LectureNotArchivedException;
import dev.knalis.education.exception.LectureNotEditableException;
import dev.knalis.education.exception.LectureNotFoundException;
import dev.knalis.education.exception.LectureStateTransitionException;
import dev.knalis.education.exception.TopicNotFoundException;
import dev.knalis.education.exception.TopicSubjectMismatchException;
import dev.knalis.education.factory.lecture.LectureFactory;
import dev.knalis.education.mapper.LectureMapper;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.LectureAttachmentRepository;
import dev.knalis.education.repository.LectureRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.repository.TopicRepository;
import dev.knalis.education.service.common.EducationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LectureService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "orderIndex",
            "createdAt",
            "updatedAt",
            "title"
    );
    private static final Set<LectureStatus> STUDENT_VISIBLE_STATUSES = Set.of(
            LectureStatus.PUBLISHED,
            LectureStatus.CLOSED
    );
    private static final Set<String> ALLOWED_FILE_KINDS = Set.of("ATTACHMENT", "DOCUMENT", "GENERIC");

    private final LectureRepository lectureRepository;
    private final LectureAttachmentRepository lectureAttachmentRepository;
    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectTeacherRepository subjectTeacherRepository;
    private final GroupStudentRepository groupStudentRepository;
    private final LectureFactory lectureFactory;
    private final LectureMapper lectureMapper;
    private final EducationAuditService educationAuditService;
    private final FileServiceClient fileServiceClient;
    private final FileServiceInternalClient fileServiceInternalClient;

    @Transactional
    public LectureResponse createLecture(
            UUID currentUserId,
            Set<String> currentRoles,
            UUID subjectId,
            UUID topicId,
            CreateLectureRequest request
    ) {
        Topic topic = requireTopic(topicId);
        ensureTopicMatchesSubject(subjectId, topic);
        ensureCanManageContent(currentUserId, currentRoles, subjectId);
        Lecture lecture = lectureFactory.newLecture(
                subjectId,
                topicId,
                request.title(),
                request.content(),
                request.orderIndex(),
                currentUserId
        );
        LectureResponse response = lectureMapper.toResponse(lectureRepository.save(lecture));
        educationAuditService.record(currentUserId, "LECTURE_CREATED", "LECTURE", response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public LectureResponse getLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanReadLecture(currentUserId, currentRoles, lecture);
        return lectureMapper.toResponse(lecture);
    }

    @Transactional(readOnly = true)
    public LecturePageResponse getLecturesByTopic(
            UUID currentUserId,
            Set<String> currentRoles,
            UUID topicId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Topic topic = requireTopic(topicId);
        ensureCanReadSubject(currentUserId, currentRoles, topic.getSubjectId());

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<Lecture> lecturePage = isStudent(currentRoles)
                ? lectureRepository.findAllByTopicIdAndStatusIn(topicId, STUDENT_VISIBLE_STATUSES, pageRequest)
                : lectureRepository.findAllByTopicId(topicId, pageRequest);
        return new LecturePageResponse(
                lecturePage.getContent().stream().map(lectureMapper::toResponse).toList(),
                lecturePage.getNumber(),
                lecturePage.getSize(),
                lecturePage.getTotalElements(),
                lecturePage.getTotalPages(),
                lecturePage.isFirst(),
                lecturePage.isLast()
        );
    }

    @Transactional
    public LectureResponse updateLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId, UpdateLectureRequest request) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new LectureNotEditableException(lectureId, lecture.getStatus());
        }
        LectureResponse oldValue = lectureMapper.toResponse(lecture);
        lectureFactory.updateLecture(lecture, request.title(), request.content(), request.orderIndex());
        LectureResponse response = lectureMapper.toResponse(lectureRepository.save(lecture));
        educationAuditService.record(currentUserId, "LECTURE_UPDATED", "LECTURE", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public LectureResponse publishLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() != LectureStatus.DRAFT && lecture.getStatus() != LectureStatus.CLOSED) {
            throw new LectureStateTransitionException(lectureId, lecture.getStatus(), LectureStatus.PUBLISHED);
        }
        return setStatus(currentUserId, lecture, LectureStatus.PUBLISHED, "LECTURE_PUBLISHED");
    }

    @Transactional
    public LectureResponse closeLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() != LectureStatus.PUBLISHED) {
            throw new LectureStateTransitionException(lectureId, lecture.getStatus(), LectureStatus.CLOSED);
        }
        return setStatus(currentUserId, lecture, LectureStatus.CLOSED, "LECTURE_CLOSED");
    }

    @Transactional
    public LectureResponse reopenLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() != LectureStatus.CLOSED) {
            throw new LectureStateTransitionException(lectureId, lecture.getStatus(), LectureStatus.PUBLISHED);
        }
        return setStatus(currentUserId, lecture, LectureStatus.PUBLISHED, "LECTURE_REOPENED");
    }

    @Transactional
    public LectureResponse archiveLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new LectureAlreadyArchivedException(lectureId);
        }
        return setStatus(currentUserId, lecture, LectureStatus.ARCHIVED, "LECTURE_ARCHIVED");
    }

    @Transactional
    public LectureResponse restoreLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() != LectureStatus.ARCHIVED) {
            throw new LectureNotArchivedException(lectureId);
        }
        return setStatus(currentUserId, lecture, LectureStatus.DRAFT, "LECTURE_RESTORED");
    }

    @Transactional
    public void deleteLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() != LectureStatus.ARCHIVED) {
            throw new LectureHasDependenciesException(lectureId, "ARCHIVE_REQUIRED");
        }
        if (lectureAttachmentRepository.existsByLectureId(lectureId)) {
            throw new LectureHasDependenciesException(lectureId, "ATTACHMENTS_PRESENT");
        }
        lectureRepository.delete(lecture);
        educationAuditService.record(currentUserId, "LECTURE_DELETED", "LECTURE", lectureId, null, null);
    }

    @Transactional(readOnly = true)
    public List<LectureAttachmentResponse> listAttachments(UUID currentUserId, Set<String> currentRoles, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanReadLecture(currentUserId, currentRoles, lecture);
        return lectureAttachmentRepository.findAllByLectureIdOrderByCreatedAtAsc(lectureId).stream()
                .map(this::toAttachmentResponse)
                .toList();
    }

    @Transactional
    public LectureAttachmentResponse addAttachment(
            UUID currentUserId,
            Set<String> currentRoles,
            String bearerToken,
            UUID lectureId,
            CreateLectureAttachmentRequest request
    ) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new LectureNotEditableException(lectureId, lecture.getStatus());
        }
        RemoteStoredFileResponse file = fileServiceClient.getMyFile(bearerToken, request.fileId());
        if (!ALLOWED_FILE_KINDS.contains(file.fileKind())) {
            throw new FileAttachmentNotAllowedException(request.fileId());
        }
        fileServiceClient.markFileActive(bearerToken, request.fileId());
        LectureAttachment attachment = new LectureAttachment();
        attachment.setLectureId(lectureId);
        attachment.setFileId(request.fileId());
        attachment.setDisplayName(normalizeDisplayName(request.displayName()));
        attachment.setOriginalFileName(file.originalFileName());
        attachment.setContentType(file.contentType());
        attachment.setSizeBytes(file.sizeBytes());
        attachment.setUploadedByUserId(currentUserId);
        LectureAttachment saved = lectureAttachmentRepository.save(attachment);
        LectureAttachmentResponse response = toAttachmentResponse(saved);
        educationAuditService.record(currentUserId, "LECTURE_ATTACHMENT_ADDED", "LECTURE", lectureId, null, response);
        return response;
    }

    @Transactional
    public void removeAttachment(UUID currentUserId, Set<String> currentRoles, UUID lectureId, UUID attachmentId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new LectureNotEditableException(lectureId, lecture.getStatus());
        }
        LectureAttachment attachment = lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)
                .orElseThrow(() -> new LectureAttachmentNotFoundException(attachmentId));
        lectureAttachmentRepository.delete(attachment);
        educationAuditService.record(currentUserId, "LECTURE_ATTACHMENT_REMOVED", "LECTURE", lectureId, null, null);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadAttachment(
            UUID currentUserId,
            Set<String> currentRoles,
            UUID lectureId,
            UUID attachmentId,
            boolean preview
    ) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        ensureCanReadLecture(currentUserId, currentRoles, lecture);
        LectureAttachment attachment = lectureAttachmentRepository.findByIdAndLectureId(attachmentId, lectureId)
                .orElseThrow(() -> new LectureAttachmentNotFoundException(attachmentId));
        return fileServiceInternalClient.download(attachment.getFileId(), preview);
    }

    @Transactional
    public LectureResponse moveLecture(UUID currentUserId, Set<String> currentRoles, UUID lectureId, UUID topicId, int orderIndex) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        Topic topic = requireTopic(topicId);
        ensureTopicMatchesSubject(lecture.getSubjectId(), topic);
        ensureCanManageContent(currentUserId, currentRoles, lecture.getSubjectId());
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new LectureNotEditableException(lectureId, lecture.getStatus());
        }
        LectureResponse oldValue = lectureMapper.toResponse(lecture);
        lecture.setTopicId(topicId);
        lecture.setOrderIndex(orderIndex);
        LectureResponse response = lectureMapper.toResponse(lectureRepository.save(lecture));
        educationAuditService.record(currentUserId, "LECTURE_MOVED", "LECTURE", lectureId, oldValue, response);
        return response;
    }

    private LectureResponse setStatus(UUID currentUserId, Lecture lecture, LectureStatus status, String auditAction) {
        LectureResponse oldValue = lectureMapper.toResponse(lecture);
        lecture.setStatus(status);
        LectureResponse response = lectureMapper.toResponse(lectureRepository.save(lecture));
        educationAuditService.record(currentUserId, auditAction, "LECTURE", response.id(), oldValue, response);
        return response;
    }

    private LectureAttachmentResponse toAttachmentResponse(LectureAttachment attachment) {
        return new LectureAttachmentResponse(
                attachment.getId(),
                attachment.getLectureId(),
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

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return displayName.trim();
    }

    private Topic requireTopic(UUID topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    private void ensureTopicMatchesSubject(UUID subjectId, Topic topic) {
        if (!topic.getSubjectId().equals(subjectId)) {
            throw new TopicSubjectMismatchException(subjectId, topic.getId());
        }
    }

    private void ensureCanReadLecture(UUID currentUserId, Set<String> currentRoles, Lecture lecture) {
        ensureCanReadSubject(currentUserId, currentRoles, lecture.getSubjectId());
        if (isStudent(currentRoles) && !STUDENT_VISIBLE_STATUSES.contains(lecture.getStatus())) {
            throw new LectureNotAccessibleException(lecture.getId());
        }
    }

    private void ensureCanReadSubject(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        if (isAdmin(currentRoles)) {
            return;
        }
        if (isTeacher(currentRoles)) {
            if (subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, currentUserId)) {
                return;
            }
            throw new EducationAccessDeniedException();
        }
        List<UUID> groupIds = currentGroupIds(currentUserId);
        if (!groupIds.isEmpty() && subjectRepository.existsByIdAndBoundGroupIds(subjectId, groupIds)) {
            return;
        }
        throw new EducationAccessDeniedException();
    }

    private void ensureCanManageContent(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        if (isAdmin(currentRoles)) {
            return;
        }
        if (isTeacher(currentRoles)
                && subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, currentUserId)) {
            return;
        }
        throw new EducationAccessDeniedException();
    }

    private List<UUID> currentGroupIds(UUID userId) {
        return groupStudentRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(GroupStudent::getGroupId)
                .distinct()
                .toList();
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "orderIndex";
        }
        return ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "orderIndex";
    }

    private Sort.Direction resolveSortDirection(String direction) {
        if ("desc".equalsIgnoreCase(direction) && "orderIndex".equalsIgnoreCase(resolveSortField("orderIndex"))) {
            return Sort.Direction.DESC;
        }
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private boolean isAdmin(Collection<String> roles) {
        return roles.contains("ROLE_OWNER") || roles.contains("ROLE_ADMIN");
    }

    private boolean isTeacher(Collection<String> roles) {
        return roles.contains("ROLE_TEACHER");
    }

    private boolean isStudent(Collection<String> roles) {
        return roles.contains("ROLE_STUDENT");
    }
}
