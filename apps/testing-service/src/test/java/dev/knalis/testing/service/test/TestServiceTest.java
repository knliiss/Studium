package dev.knalis.testing.service.test;

import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.client.education.dto.GroupMembershipResponse;
import dev.knalis.testing.client.education.dto.SubjectResponse;
import dev.knalis.testing.client.education.dto.TopicResponse;
import dev.knalis.testing.dto.request.CreateTestRequest;
import dev.knalis.testing.dto.response.TestPageResponse;
import dev.knalis.testing.dto.response.TestResponse;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestGroupAvailability;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.factory.attempt.TestAttemptFactory;
import dev.knalis.testing.factory.test.TestFactory;
import dev.knalis.testing.mapper.TestMapper;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.TestGroupAvailabilityRepository;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.service.common.TestingAuditService;
import dev.knalis.testing.service.common.TestingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestServiceTest {
    
    @Mock
    private TestRepository testRepository;

    @Mock
    private TestAttemptRepository testAttemptRepository;

    @Mock
    private TestGroupAvailabilityRepository testGroupAvailabilityRepository;

    @Mock
    private QuestionRepository questionRepository;
    
    @Mock
    private TestMapper testMapper;

    @Mock
    private TestingAuditService testingAuditService;
    
    @Mock
    private TestingEventPublisher testingEventPublisher;
    
    @Mock
    private EducationServiceClient educationServiceClient;
    
    private TestService testService;
    
    @BeforeEach
    void setUp() {
        testService = new TestService(
                testRepository,
                testAttemptRepository,
                testGroupAvailabilityRepository,
                questionRepository,
                new TestFactory(),
                new TestAttemptFactory(),
                testMapper,
                testingAuditService,
                testingEventPublisher,
                educationServiceClient
        );
    }
    
    @org.junit.jupiter.api.Test
    void createTestSavesTrimmedTitle() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Test savedTest = new Test();
        savedTest.setId(testId);
        savedTest.setTopicId(topicId);
        savedTest.setTitle("Quiz 1");
        savedTest.setOrderIndex(0);
        savedTest.setStatus(TestStatus.DRAFT);
        savedTest.setMaxAttempts(1);
        savedTest.setMaxPoints(100);
        savedTest.setCreatedAt(now);
        savedTest.setUpdatedAt(now);
        
        TestResponse response = new TestResponse(
                testId,
                topicId,
                "Quiz 1",
                0,
                TestStatus.DRAFT,
                1,
                100,
                null,
                null,
                null,
                false,
                false,
                false,
                now,
                now
        );
        
        when(testRepository.save(any(Test.class))).thenReturn(savedTest);
        when(testMapper.toResponse(savedTest)).thenReturn(response);
        
        TestResponse result = testService.createTest(
                UUID.randomUUID(),
                true,
                new CreateTestRequest(topicId, "  Quiz 1  ", null, null, null, null, null, null, null, null, null)
        );
        
        ArgumentCaptor<Test> captor = ArgumentCaptor.forClass(Test.class);
        verify(testRepository).save(captor.capture());
        assertEquals("Quiz 1", captor.getValue().getTitle());
        assertEquals(response, result);
    }
    
    @org.junit.jupiter.api.Test
    void getTestsByTopicReturnsPageResponse() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Test entity = new Test();
        entity.setId(testId);
        entity.setTopicId(topicId);
        entity.setTitle("Quiz 1");
        entity.setOrderIndex(0);
        entity.setStatus(TestStatus.PUBLISHED);
        entity.setMaxAttempts(1);
        entity.setMaxPoints(100);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        TestResponse response = new TestResponse(
                testId,
                topicId,
                "Quiz 1",
                0,
                TestStatus.PUBLISHED,
                1,
                100,
                null,
                null,
                null,
                false,
                false,
                false,
                now,
                now
        );
        
        when(testRepository.findAllByTopicId(eq(topicId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(testMapper.toResponse(entity)).thenReturn(response);
        
        TestPageResponse result = testService.getTestsByTopic(
                topicId,
                UUID.randomUUID(),
                0,
                20,
                null,
                null,
                true,
                false
        );
        
        assertEquals(List.of(response), result.items());
        assertEquals(1L, result.totalElements());
    }

    @org.junit.jupiter.api.Test
    void getTestsByTopicReturnsDraftsForAssignedTeacher() {
        UUID teacherId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Instant now = Instant.now();

        Test entity = new Test();
        entity.setId(testId);
        entity.setTopicId(topicId);
        entity.setTitle("Quiz 1");
        entity.setOrderIndex(0);
        entity.setStatus(TestStatus.DRAFT);
        entity.setMaxAttempts(1);
        entity.setMaxPoints(100);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        TestResponse response = new TestResponse(
                testId,
                topicId,
                "Quiz 1",
                0,
                TestStatus.DRAFT,
                1,
                100,
                null,
                null,
                null,
                false,
                false,
                false,
                now,
                now
        );

        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Topic",
                0,
                now,
                now
        ));
        when(educationServiceClient.getSubject(subjectId)).thenReturn(new SubjectResponse(
                subjectId,
                "Algorithms",
                UUID.randomUUID(),
                List.of(UUID.randomUUID()),
                List.of(teacherId),
                "Course",
                now,
                now
        ));
        when(testRepository.findAllByTopicId(eq(topicId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(testMapper.toResponse(entity)).thenReturn(response);

        TestPageResponse result = testService.getTestsByTopic(
                topicId,
                teacherId,
                0,
                20,
                null,
                null,
                false,
                true
        );

        assertEquals(List.of(response), result.items());
        verify(testRepository).findAllByTopicId(eq(topicId), any(Pageable.class));
    }
    
    @org.junit.jupiter.api.Test
    void startTestPublishesStartedEvent() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setTitle("Quiz 1");
        test.setOrderIndex(0);
        test.setStatus(TestStatus.PUBLISHED);
        test.setMaxAttempts(1);
        test.setMaxPoints(100);
        test.setCreatedAt(now);
        test.setUpdatedAt(now);

        TestGroupAvailability availability = new TestGroupAvailability();
        availability.setId(UUID.randomUUID());
        availability.setTestId(testId);
        availability.setGroupId(groupId);
        availability.setVisible(true);
        availability.setMaxAttempts(1);
        availability.setCreatedAt(now);
        availability.setUpdatedAt(now);
        
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(educationServiceClient.getGroupsByUser(userId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(testGroupAvailabilityRepository.findAvailableForTestAndGroups(eq(testId), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.empty());
        when(testAttemptRepository.countByTestIdAndUserId(testId, userId)).thenReturn(0L);
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Topic",
                1,
                now,
                now
        ));
        
        testService.startTest(userId, testId);
        
        verify(testingEventPublisher).publishTestStarted(any());
    }

    @org.junit.jupiter.api.Test
    void publishTestPublishesPublishedEvent() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant now = Instant.now();

        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setTitle("Quiz 1");
        test.setOrderIndex(0);
        test.setStatus(TestStatus.DRAFT);
        test.setMaxAttempts(1);
        test.setMaxPoints(100);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(questionRepository.sumPointsByTestId(testId)).thenReturn(100);
        when(testRepository.save(test)).thenReturn(test);
        when(testMapper.toResponse(test)).thenReturn(new TestResponse(
                testId,
                topicId,
                "Quiz 1",
                0,
                TestStatus.PUBLISHED,
                1,
                100,
                null,
                null,
                null,
                false,
                false,
                false,
                now,
                now
        ));

        testService.publishTest(UUID.randomUUID(), true, testId);

        assertEquals(TestStatus.PUBLISHED, test.getStatus());
        verify(testingEventPublisher).publishTestPublished(any());
    }

    @org.junit.jupiter.api.Test
    void publishTestRejectsQuestionPointsAboveMaxPoints() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setTitle("Quiz 1");
        test.setOrderIndex(0);
        test.setStatus(TestStatus.DRAFT);
        test.setMaxAttempts(1);
        test.setMaxPoints(10);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(questionRepository.sumPointsByTestId(testId)).thenReturn(11);

        assertThrows(
                TestInvalidStateException.class,
                () -> testService.publishTest(UUID.randomUUID(), true, testId)
        );
    }
}
