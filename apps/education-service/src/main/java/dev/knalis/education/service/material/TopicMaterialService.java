package dev.knalis.education.service.material;

import dev.knalis.education.client.file.FileServiceClient;
import dev.knalis.education.client.file.dto.RemoteStoredFileResponse;
import dev.knalis.education.client.file.internal.FileServiceInternalClient;
import dev.knalis.education.dto.request.CreateTopicMaterialRequest;
import dev.knalis.education.dto.request.UpdateTopicMaterialRequest;
import dev.knalis.education.dto.response.TopicMaterialPageResponse;
import dev.knalis.education.dto.response.TopicMaterialResponse;
import dev.knalis.education.entity.GroupStudent;
import dev.knalis.education.entity.Topic;
import dev.knalis.education.entity.TopicMaterial;
import dev.knalis.education.entity.TopicMaterialType;
import dev.knalis.education.exception.EducationAccessDeniedException;
import dev.knalis.education.exception.FileAttachmentNotAllowedException;
import dev.knalis.education.exception.TopicNotFoundException;
import dev.knalis.education.exception.TopicMaterialDeleteNotAllowedException;
import dev.knalis.education.exception.TopicMaterialNotAccessibleException;
import dev.knalis.education.exception.TopicMaterialNotFoundException;
import dev.knalis.education.exception.TopicMaterialValidationException;
import dev.knalis.education.mapper.TopicMaterialMapper;
import dev.knalis.education.repository.GroupStudentRepository;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.repository.TopicMaterialRepository;
import dev.knalis.education.repository.TopicRepository;
import dev.knalis.education.service.common.EducationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopicMaterialService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("orderIndex", "createdAt", "updatedAt", "title");
    private static final Set<String> ALLOWED_FILE_KINDS = Set.of("ATTACHMENT", "DOCUMENT", "GENERIC");

    private final TopicMaterialRepository topicMaterialRepository;
    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectTeacherRepository subjectTeacherRepository;
    private final GroupStudentRepository groupStudentRepository;
    private final TopicMaterialMapper topicMaterialMapper;
    private final EducationAuditService educationAuditService;
    private final FileServiceClient fileServiceClient;
    private final FileServiceInternalClient fileServiceInternalClient;

    @Transactional
    public TopicMaterialResponse createMaterial(
            UUID currentUserId,
            Set<String> currentRoles,
            String bearerToken,
            UUID topicId,
            CreateTopicMaterialRequest request
    ) {
        Topic topic = requireTopic(topicId);
        ensureCanManage(currentUserId, currentRoles, topic.getSubjectId());

        TopicMaterial material = new TopicMaterial();
        material.setTopicId(topicId);
        material.setTitle(request.title().trim());
        material.setDescription(normalizeText(request.description()));
        material.setType(request.type());
        material.setVisible(request.visible() == null || request.visible());
        material.setArchived(false);
        material.setOrderIndex(request.orderIndex() == null ? 0 : request.orderIndex());
        material.setCreatedByUserId(currentUserId);

        applyTypeSpecificFields(material, request.type(), request.url(), request.fileId(), bearerToken);

        TopicMaterial saved = topicMaterialRepository.save(material);
        TopicMaterialResponse response = topicMaterialMapper.toResponse(saved);
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_CREATED", "TOPIC_MATERIAL", response.id(), null, response);
        return response;
    }

    @Transactional
    public TopicMaterialResponse updateMaterial(
            UUID currentUserId,
            Set<String> currentRoles,
            String bearerToken,
            UUID materialId,
            UpdateTopicMaterialRequest request
    ) {
        TopicMaterial material = topicMaterialRepository.findById(materialId)
                .orElseThrow(() -> new TopicMaterialNotFoundException(materialId));
        Topic topic = requireTopic(material.getTopicId());
        ensureCanManage(currentUserId, currentRoles, topic.getSubjectId());

        TopicMaterialResponse oldValue = topicMaterialMapper.toResponse(material);
        material.setTitle(request.title().trim());
        material.setDescription(normalizeText(request.description()));
        material.setType(request.type());
        material.setVisible(request.visible() == null || request.visible());
        material.setOrderIndex(request.orderIndex() == null ? material.getOrderIndex() : request.orderIndex());

        applyTypeSpecificFields(material, request.type(), request.url(), request.fileId(), bearerToken);

        TopicMaterial saved = topicMaterialRepository.save(material);
        TopicMaterialResponse response = topicMaterialMapper.toResponse(saved);
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_UPDATED", "TOPIC_MATERIAL", response.id(), oldValue, response);
        return response;
    }

    @Transactional(readOnly = true)
    public TopicMaterialResponse getMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId) {
        TopicMaterial material = topicMaterialRepository.findById(materialId)
                .orElseThrow(() -> new TopicMaterialNotFoundException(materialId));
        Topic topic = requireTopic(material.getTopicId());
        ensureCanRead(currentUserId, currentRoles, topic.getSubjectId());
        if (isStudent(currentRoles) && (!material.isVisible() || material.isArchived())) {
            throw new TopicMaterialNotAccessibleException(materialId);
        }
        return topicMaterialMapper.toResponse(material);
    }

    @Transactional(readOnly = true)
    public TopicMaterialPageResponse getMaterialsByTopic(
            UUID currentUserId,
            Set<String> currentRoles,
            UUID topicId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Topic topic = requireTopic(topicId);
        ensureCanRead(currentUserId, currentRoles, topic.getSubjectId());

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<TopicMaterial> materialPage = isStudent(currentRoles)
                ? topicMaterialRepository.findAllByTopicIdAndVisibleIsTrueAndArchivedIsFalse(topicId, pageRequest)
                : topicMaterialRepository.findAllByTopicId(topicId, pageRequest);

        return new TopicMaterialPageResponse(
                materialPage.getContent().stream().map(topicMaterialMapper::toResponse).toList(),
                materialPage.getNumber(),
                materialPage.getSize(),
                materialPage.getTotalElements(),
                materialPage.getTotalPages(),
                materialPage.isFirst(),
                materialPage.isLast()
        );
    }

    @Transactional
    public TopicMaterialResponse publishMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId) {
        TopicMaterial material = requireManageableMaterial(currentUserId, currentRoles, materialId);
        TopicMaterialResponse oldValue = topicMaterialMapper.toResponse(material);
        material.setVisible(true);
        TopicMaterialResponse response = topicMaterialMapper.toResponse(topicMaterialRepository.save(material));
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_PUBLISHED", "TOPIC_MATERIAL", materialId, oldValue, response);
        return response;
    }

    @Transactional
    public TopicMaterialResponse hideMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId) {
        TopicMaterial material = requireManageableMaterial(currentUserId, currentRoles, materialId);
        TopicMaterialResponse oldValue = topicMaterialMapper.toResponse(material);
        material.setVisible(false);
        TopicMaterialResponse response = topicMaterialMapper.toResponse(topicMaterialRepository.save(material));
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_HIDDEN", "TOPIC_MATERIAL", materialId, oldValue, response);
        return response;
    }

    @Transactional
    public TopicMaterialResponse archiveMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId) {
        TopicMaterial material = requireManageableMaterial(currentUserId, currentRoles, materialId);
        TopicMaterialResponse oldValue = topicMaterialMapper.toResponse(material);
        material.setArchived(true);
        TopicMaterialResponse response = topicMaterialMapper.toResponse(topicMaterialRepository.save(material));
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_ARCHIVED", "TOPIC_MATERIAL", materialId, oldValue, response);
        return response;
    }

    @Transactional
    public TopicMaterialResponse restoreMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId) {
        TopicMaterial material = requireManageableMaterial(currentUserId, currentRoles, materialId);
        TopicMaterialResponse oldValue = topicMaterialMapper.toResponse(material);
        material.setArchived(false);
        TopicMaterialResponse response = topicMaterialMapper.toResponse(topicMaterialRepository.save(material));
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_RESTORED", "TOPIC_MATERIAL", materialId, oldValue, response);
        return response;
    }

    @Transactional
    public void deleteMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId) {
        TopicMaterial material = requireManageableMaterial(currentUserId, currentRoles, materialId);
        if (!material.isArchived()) {
            throw new TopicMaterialDeleteNotAllowedException(materialId);
        }
        topicMaterialRepository.delete(material);
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_DELETED", "TOPIC_MATERIAL", materialId, null, null);
    }

    @Transactional
    public TopicMaterialResponse moveMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId, UUID topicId, int orderIndex) {
        TopicMaterial material = topicMaterialRepository.findById(materialId)
                .orElseThrow(() -> new TopicMaterialNotFoundException(materialId));
        Topic currentTopic = requireTopic(material.getTopicId());
        Topic targetTopic = requireTopic(topicId);
        ensureCanManage(currentUserId, currentRoles, currentTopic.getSubjectId());
        ensureCanManage(currentUserId, currentRoles, targetTopic.getSubjectId());
        TopicMaterialResponse oldValue = topicMaterialMapper.toResponse(material);
        material.setTopicId(topicId);
        material.setOrderIndex(orderIndex);
        TopicMaterialResponse response = topicMaterialMapper.toResponse(topicMaterialRepository.save(material));
        educationAuditService.record(currentUserId, "TOPIC_MATERIAL_MOVED", "TOPIC_MATERIAL", materialId, oldValue, response);
        return response;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadFile(UUID currentUserId, Set<String> currentRoles, UUID materialId, boolean preview) {
        TopicMaterial material = topicMaterialRepository.findById(materialId)
                .orElseThrow(() -> new TopicMaterialNotFoundException(materialId));
        Topic topic = requireTopic(material.getTopicId());
        ensureCanRead(currentUserId, currentRoles, topic.getSubjectId());
        if (isStudent(currentRoles) && (!material.isVisible() || material.isArchived())) {
            throw new TopicMaterialNotAccessibleException(materialId);
        }
        if (material.getType() != TopicMaterialType.FILE || material.getFileId() == null) {
            throw new TopicMaterialValidationException("type", "File material is required");
        }
        return fileServiceInternalClient.download(material.getFileId(), preview);
    }

    private TopicMaterial requireManageableMaterial(UUID currentUserId, Set<String> currentRoles, UUID materialId) {
        TopicMaterial material = topicMaterialRepository.findById(materialId)
                .orElseThrow(() -> new TopicMaterialNotFoundException(materialId));
        Topic topic = requireTopic(material.getTopicId());
        ensureCanManage(currentUserId, currentRoles, topic.getSubjectId());
        return material;
    }

    private Topic requireTopic(UUID topicId) {
        return topicRepository.findById(topicId).orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    private void applyTypeSpecificFields(TopicMaterial material, TopicMaterialType type, String url, UUID fileId, String bearerToken) {
        material.setUrl(null);
        material.setFileId(null);
        material.setOriginalFileName(null);
        material.setContentType(null);
        material.setSizeBytes(null);

        if (type == TopicMaterialType.FILE) {
            if (fileId == null) {
                throw new TopicMaterialValidationException("fileId", "File is required");
            }
            RemoteStoredFileResponse file = fileServiceClient.getMyFile(bearerToken, fileId);
            if (!ALLOWED_FILE_KINDS.contains(file.fileKind())) {
                throw new FileAttachmentNotAllowedException(fileId);
            }
            fileServiceClient.markFileActive(bearerToken, fileId);
            material.setFileId(fileId);
            material.setOriginalFileName(file.originalFileName());
            material.setContentType(file.contentType());
            material.setSizeBytes(file.sizeBytes());
            return;
        }

        if (type == TopicMaterialType.LINK) {
            String normalizedUrl = normalizeText(url);
            if (normalizedUrl == null || !isValidHttpUrl(normalizedUrl)) {
                throw new TopicMaterialValidationException("url", "Valid URL is required");
            }
            material.setUrl(normalizedUrl);
            return;
        }

        String description = normalizeText(material.getDescription());
        if (description == null) {
            throw new TopicMaterialValidationException("description", "Content is required");
        }
        material.setDescription(description);
    }

    private boolean isValidHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (Exception exception) {
            return false;
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void ensureCanRead(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
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

    private void ensureCanManage(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        if (isAdmin(currentRoles)) {
            return;
        }
        if (isTeacher(currentRoles) && subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, currentUserId)) {
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
