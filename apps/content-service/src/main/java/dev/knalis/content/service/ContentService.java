package dev.knalis.content.service;

import dev.knalis.content.client.education.EducationServiceClient;
import dev.knalis.content.client.education.dto.SubjectResponse;
import dev.knalis.content.dto.request.CreateLectureMaterialRequest;
import dev.knalis.content.dto.request.CreateLectureRequest;
import dev.knalis.content.dto.request.CreateTopicMaterialRequest;
import dev.knalis.content.dto.request.UpdateLectureRequest;
import dev.knalis.content.dto.response.LectureMaterialResponse;
import dev.knalis.content.dto.response.LectureResponse;
import dev.knalis.content.dto.response.TopicMaterialResponse;
import dev.knalis.content.entity.Lecture;
import dev.knalis.content.entity.LectureMaterial;
import dev.knalis.content.entity.LectureStatus;
import dev.knalis.content.entity.TopicMaterial;
import dev.knalis.content.exception.ContentAccessDeniedException;
import dev.knalis.content.exception.LectureMaterialNotFoundException;
import dev.knalis.content.exception.LectureNotFoundException;
import dev.knalis.content.exception.TopicMaterialNotFoundException;
import dev.knalis.content.repository.LectureMaterialRepository;
import dev.knalis.content.repository.LectureRepository;
import dev.knalis.content.repository.TopicMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final TopicMaterialRepository topicMaterialRepository;
    private final LectureRepository lectureRepository;
    private final LectureMaterialRepository lectureMaterialRepository;
    private final EducationServiceClient educationServiceClient;

    @Transactional
    public TopicMaterialResponse createTopicMaterial(
            UUID currentUserId,
            boolean privilegedAccess,
            CreateTopicMaterialRequest request
    ) {
        assertTeacherOwnership(request.topicId(), currentUserId, privilegedAccess);
        TopicMaterial material = new TopicMaterial();
        material.setTopicId(request.topicId());
        material.setFileId(request.fileId());
        material.setTitle(request.title().trim());
        material.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        material.setVisible(request.visible() == null || request.visible());
        material.setCreatedByUserId(currentUserId);
        return toResponse(topicMaterialRepository.save(material));
    }

    @Transactional(readOnly = true)
    public List<TopicMaterialResponse> getTopicMaterials(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherRole,
            UUID topicId
    ) {
        if (privilegedAccess || teacherRole) {
            assertTeacherOwnership(topicId, currentUserId, privilegedAccess);
            return topicMaterialRepository.findAllByTopicIdOrderByCreatedAtAsc(topicId).stream()
                    .map(this::toResponse)
                    .toList();
        }
        return topicMaterialRepository.findAllByTopicIdAndVisibleTrueOrderByCreatedAtAsc(topicId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteTopicMaterial(UUID currentUserId, boolean privilegedAccess, UUID topicMaterialId) {
        TopicMaterial material = topicMaterialRepository.findById(topicMaterialId)
                .orElseThrow(() -> new TopicMaterialNotFoundException(topicMaterialId));
        assertTeacherOwnership(material.getTopicId(), currentUserId, privilegedAccess);
        topicMaterialRepository.delete(material);
    }

    @Transactional
    public LectureResponse createLecture(
            UUID currentUserId,
            boolean privilegedAccess,
            CreateLectureRequest request
    ) {
        assertTeacherOwnership(request.topicId(), currentUserId, privilegedAccess);
        Lecture lecture = new Lecture();
        lecture.setTopicId(request.topicId());
        lecture.setTitle(request.title().trim());
        lecture.setSummary(request.summary() == null || request.summary().isBlank() ? null : request.summary().trim());
        lecture.setContent(request.content().trim());
        lecture.setOrderIndex(request.orderIndex());
        lecture.setStatus(LectureStatus.DRAFT);
        lecture.setCreatedByUserId(currentUserId);
        return toResponse(lectureRepository.save(lecture));
    }

    @Transactional
    public LectureResponse updateLecture(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID lectureId,
            UpdateLectureRequest request
    ) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        assertTeacherOwnership(lecture.getTopicId(), currentUserId, privilegedAccess);

        if (request.title() != null && !request.title().isBlank()) {
            lecture.setTitle(request.title().trim());
        }
        if (request.summary() != null) {
            lecture.setSummary(request.summary().isBlank() ? null : request.summary().trim());
        }
        if (request.content() != null && !request.content().isBlank()) {
            lecture.setContent(request.content().trim());
        }
        if (request.orderIndex() != null) {
            lecture.setOrderIndex(request.orderIndex());
        }

        return toResponse(lectureRepository.save(lecture));
    }

    @Transactional
    public LectureResponse publishLecture(UUID currentUserId, boolean privilegedAccess, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        assertTeacherOwnership(lecture.getTopicId(), currentUserId, privilegedAccess);
        lecture.setStatus(LectureStatus.PUBLISHED);
        return toResponse(lectureRepository.save(lecture));
    }

    @Transactional
    public LectureResponse archiveLecture(UUID currentUserId, boolean privilegedAccess, UUID lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        assertTeacherOwnership(lecture.getTopicId(), currentUserId, privilegedAccess);
        lecture.setStatus(LectureStatus.ARCHIVED);
        return toResponse(lectureRepository.save(lecture));
    }

    @Transactional(readOnly = true)
    public LectureResponse getLecture(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherRole,
            UUID lectureId
    ) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        if (!privilegedAccess && !teacherRole && lecture.getStatus() != LectureStatus.PUBLISHED) {
            throw new LectureNotFoundException(lectureId);
        }
        if (teacherRole) {
            assertTeacherOwnership(lecture.getTopicId(), currentUserId, privilegedAccess);
        }
        return toResponse(lecture);
    }

    @Transactional(readOnly = true)
    public List<LectureResponse> getLecturesByTopic(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherRole,
            UUID topicId
    ) {
        if (privilegedAccess || teacherRole) {
            assertTeacherOwnership(topicId, currentUserId, privilegedAccess);
            return lectureRepository.findAllByTopicIdOrderByOrderIndexAscCreatedAtAsc(topicId).stream()
                    .map(this::toResponse)
                    .toList();
        }
        return lectureRepository.findAllByTopicIdAndStatusOrderByOrderIndexAscCreatedAtAsc(topicId, LectureStatus.PUBLISHED).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LectureMaterialResponse addLectureMaterial(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID lectureId,
            CreateLectureMaterialRequest request
    ) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        assertTeacherOwnership(lecture.getTopicId(), currentUserId, privilegedAccess);

        LectureMaterial material = new LectureMaterial();
        material.setLectureId(lectureId);
        material.setFileId(request.fileId());
        material.setTitle(request.title().trim());
        material.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        return toResponse(lectureMaterialRepository.save(material));
    }

    @Transactional(readOnly = true)
    public List<LectureMaterialResponse> getLectureMaterials(
            UUID currentUserId,
            boolean privilegedAccess,
            boolean teacherRole,
            UUID lectureId
    ) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new LectureNotFoundException(lectureId));
        if (teacherRole) {
            assertTeacherOwnership(lecture.getTopicId(), currentUserId, privilegedAccess);
        } else if (!privilegedAccess && lecture.getStatus() != LectureStatus.PUBLISHED) {
            throw new LectureNotFoundException(lectureId);
        }
        return lectureMaterialRepository.findAllByLectureIdOrderByCreatedAtAsc(lectureId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteLectureMaterial(UUID currentUserId, boolean privilegedAccess, UUID lectureMaterialId) {
        LectureMaterial material = lectureMaterialRepository.findById(lectureMaterialId)
                .orElseThrow(() -> new LectureMaterialNotFoundException(lectureMaterialId));
        Lecture lecture = lectureRepository.findById(material.getLectureId())
                .orElseThrow(() -> new LectureNotFoundException(material.getLectureId()));
        assertTeacherOwnership(lecture.getTopicId(), currentUserId, privilegedAccess);
        lectureMaterialRepository.delete(material);
    }

    private void assertTeacherOwnership(UUID topicId, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return;
        }
        UUID subjectId = educationServiceClient.getTopic(topicId).subjectId();
        SubjectResponse subject = educationServiceClient.getSubject(subjectId);
        if (subject.teacherIds() != null && subject.teacherIds().contains(currentUserId)) {
            return;
        }
        throw new ContentAccessDeniedException(topicId, currentUserId);
    }

    private TopicMaterialResponse toResponse(TopicMaterial material) {
        return new TopicMaterialResponse(
                material.getId(),
                material.getTopicId(),
                material.getFileId(),
                material.getTitle(),
                material.getDescription(),
                material.isVisible(),
                material.getCreatedByUserId(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }

    private LectureResponse toResponse(Lecture lecture) {
        return new LectureResponse(
                lecture.getId(),
                lecture.getTopicId(),
                lecture.getTitle(),
                lecture.getSummary(),
                lecture.getContent(),
                lecture.getOrderIndex(),
                lecture.getStatus(),
                lecture.getCreatedByUserId(),
                lecture.getCreatedAt(),
                lecture.getUpdatedAt()
        );
    }

    private LectureMaterialResponse toResponse(LectureMaterial material) {
        return new LectureMaterialResponse(
                material.getId(),
                material.getLectureId(),
                material.getFileId(),
                material.getTitle(),
                material.getDescription(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
