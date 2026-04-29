package dev.knalis.education.service.topic;

import dev.knalis.education.dto.request.CreateTopicRequest;
import dev.knalis.education.dto.response.TopicPageResponse;
import dev.knalis.education.dto.response.TopicResponse;
import dev.knalis.education.entity.Topic;
import dev.knalis.education.exception.TopicOrderAlreadyExistsException;
import dev.knalis.education.factory.topic.TopicFactory;
import dev.knalis.education.mapper.TopicMapper;
import dev.knalis.education.repository.SubjectRepository;
import dev.knalis.education.repository.SubjectTeacherRepository;
import dev.knalis.education.repository.TopicRepository;
import dev.knalis.education.service.common.EducationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {
    
    @Mock
    private TopicRepository topicRepository;
    
    @Mock
    private SubjectRepository subjectRepository;
    
    @Mock
    private SubjectTeacherRepository subjectTeacherRepository;

    @Mock
    private TopicMapper topicMapper;

    @Mock
    private EducationAuditService educationAuditService;
    
    private TopicService topicService;
    
    @BeforeEach
    void setUp() {
        topicService = new TopicService(
                topicRepository,
                subjectRepository,
                subjectTeacherRepository,
                new TopicFactory(),
                topicMapper,
                educationAuditService
        );
    }
    
    @Test
    void createTopicThrowsWhenOrderIndexAlreadyExists() {
        UUID subjectId = UUID.randomUUID();
        
        when(subjectRepository.existsById(subjectId)).thenReturn(true);
        when(topicRepository.existsBySubjectIdAndOrderIndex(subjectId, 1)).thenReturn(true);
        
        assertThrows(
                TopicOrderAlreadyExistsException.class,
                () -> topicService.createTopic(
                        UUID.randomUUID(),
                        Set.of("ROLE_ADMIN"),
                        new CreateTopicRequest(subjectId, "Introduction", 1)
                )
        );
    }
    
    @Test
    void getTopicsBySubjectReturnsPageResponse() {
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Topic topic = new Topic();
        topic.setId(topicId);
        topic.setSubjectId(subjectId);
        topic.setTitle("Introduction");
        topic.setOrderIndex(1);
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        
        TopicResponse topicResponse = new TopicResponse(
                topicId,
                subjectId,
                "Introduction",
                1,
                now,
                now
        );
        
        when(subjectRepository.existsById(subjectId)).thenReturn(true);
        when(topicRepository.findAllBySubjectId(eq(subjectId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(topic)));
        when(topicMapper.toResponse(topic)).thenReturn(topicResponse);
        
        TopicPageResponse result = topicService.getTopicsBySubject(subjectId, 0, 20, null, null);
        
        assertEquals(List.of(topicResponse), result.items());
        assertEquals(1L, result.totalElements());
        assertEquals(1, result.totalPages());
    }
}
