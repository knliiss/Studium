package dev.knalis.education.service.topic;

import dev.knalis.education.dto.request.CreateTopicRequest;
import dev.knalis.education.dto.request.ReorderTopicsRequest;
import dev.knalis.education.dto.request.UpdateTopicRequest;
import dev.knalis.education.dto.response.TopicPageResponse;
import dev.knalis.education.dto.response.TopicResponse;
import dev.knalis.education.entity.Topic;
import dev.knalis.education.exception.EducationAccessDeniedException;
import dev.knalis.education.exception.SubjectNotFoundException;
import dev.knalis.education.exception.TopicNotFoundException;
import dev.knalis.education.exception.TopicOrderAlreadyExistsException;
import dev.knalis.education.factory.topic.TopicFactory;
import dev.knalis.education.mapper.TopicMapper;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.repository.TopicRepository;
import dev.knalis.education.service.common.EducationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TopicService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "orderIndex",
            "createdAt",
            "updatedAt",
            "title"
    );
    
    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectTeacherRepository subjectTeacherRepository;
    private final TopicFactory topicFactory;
    private final TopicMapper topicMapper;
    private final EducationAuditService educationAuditService;
    
    @Transactional
    public TopicResponse createTopic(UUID currentUserId, Set<String> currentRoles, CreateTopicRequest request) {
        ensureSubjectExists(request.subjectId());
        ensureCanManageContent(currentUserId, currentRoles, request.subjectId());
        if (topicRepository.existsBySubjectIdAndOrderIndex(request.subjectId(), request.orderIndex())) {
            throw new TopicOrderAlreadyExistsException(request.subjectId(), request.orderIndex());
        }
        Topic topic = topicFactory.newTopic(request.subjectId(), request.title(), request.orderIndex());
        TopicResponse response = topicMapper.toResponse(topicRepository.save(topic));
        educationAuditService.record(currentUserId, "TOPIC_CREATED", "TOPIC", response.id(), null, response);
        return response;
    }

    @Transactional
    public TopicResponse updateTopic(UUID currentUserId, Set<String> currentRoles, UUID topicId, UpdateTopicRequest request) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        ensureCanManageContent(currentUserId, currentRoles, topic.getSubjectId());
        TopicResponse oldValue = topicMapper.toResponse(topic);
        boolean orderChanged = topic.getOrderIndex() != request.orderIndex();
        if (orderChanged && topicRepository.existsBySubjectIdAndOrderIndex(topic.getSubjectId(), request.orderIndex())) {
            throw new TopicOrderAlreadyExistsException(topic.getSubjectId(), request.orderIndex());
        }

        topic.setTitle(request.title().trim());
        topic.setOrderIndex(request.orderIndex());
        TopicResponse response = topicMapper.toResponse(topicRepository.save(topic));
        educationAuditService.record(currentUserId, "TOPIC_UPDATED", "TOPIC", response.id(), oldValue, response);
        return response;
    }

    @Transactional
    public List<TopicResponse> reorderTopics(
            UUID currentUserId,
            Set<String> currentRoles,
            UUID subjectId,
            ReorderTopicsRequest request
    ) {
        ensureSubjectExists(subjectId);
        ensureCanManageContent(currentUserId, currentRoles, subjectId);
        List<Topic> currentTopics = topicRepository.findAllBySubjectIdOrderByOrderIndexAscCreatedAtAsc(subjectId);
        Map<UUID, Integer> originalOrderByTopicId = currentTopics.stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getOrderIndex));
        Set<UUID> subjectTopicIds = currentTopics.stream()
                .map(Topic::getId)
                .collect(Collectors.toSet());
        for (ReorderTopicsRequest.TopicOrderItem item : request.topics()) {
            if (!subjectTopicIds.contains(item.topicId())) {
                throw new TopicNotFoundException(item.topicId());
            }
        }

        for (int index = 0; index < currentTopics.size(); index += 1) {
            currentTopics.get(index).setOrderIndex(-100000 - index);
        }
        topicRepository.saveAllAndFlush(currentTopics);

        var orderByTopicId = request.topics().stream()
                .collect(Collectors.toMap(
                        ReorderTopicsRequest.TopicOrderItem::topicId,
                        ReorderTopicsRequest.TopicOrderItem::orderIndex
                ));
        for (Topic topic : currentTopics) {
            topic.setOrderIndex(orderByTopicId.getOrDefault(topic.getId(), originalOrderByTopicId.get(topic.getId())));
        }
        List<TopicResponse> response = topicRepository.saveAll(currentTopics).stream()
                .sorted(Comparator.comparingInt(Topic::getOrderIndex))
                .map(topicMapper::toResponse)
                .toList();
        educationAuditService.record(currentUserId, "TOPICS_REORDERED", "SUBJECT", subjectId, null, response);
        return response;
    }
    
    @Transactional(readOnly = true)
    public TopicPageResponse getTopicsBySubject(UUID subjectId, int page, int size, String sortBy, String direction) {
        ensureSubjectExists(subjectId);
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<Topic> topicPage = topicRepository.findAllBySubjectId(subjectId, pageRequest);
        
        return new TopicPageResponse(
                topicPage.getContent().stream().map(topicMapper::toResponse).toList(),
                topicPage.getNumber(),
                topicPage.getSize(),
                topicPage.getTotalElements(),
                topicPage.getTotalPages(),
                topicPage.isFirst(),
                topicPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> getTopicsBySubject(UUID subjectId) {
        ensureSubjectExists(subjectId);
        return topicRepository.findAllBySubjectIdOrderByOrderIndexAscCreatedAtAsc(subjectId).stream()
                .map(topicMapper::toResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public TopicResponse getTopic(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        return topicMapper.toResponse(topic);
    }
    
    private void ensureSubjectExists(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new SubjectNotFoundException(subjectId);
        }
    }

    private void ensureCanManageContent(UUID currentUserId, Set<String> currentRoles, UUID subjectId) {
        if (currentRoles.contains("ROLE_OWNER") || currentRoles.contains("ROLE_ADMIN")) {
            return;
        }
        if (currentRoles.contains("ROLE_TEACHER")
                && subjectTeacherRepository.existsBySubjectIdAndTeacherId(subjectId, currentUserId)) {
            return;
        }
        throw new EducationAccessDeniedException();
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
}
