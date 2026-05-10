package dev.knalis.testing.service.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.client.education.dto.GroupMembershipResponse;
import dev.knalis.testing.client.education.dto.SubjectResponse;
import dev.knalis.testing.client.education.dto.TopicResponse;
import dev.knalis.testing.dto.request.CreateTestRequest;
import dev.knalis.testing.dto.response.TestPageResponse;
import dev.knalis.testing.dto.response.TestPreviewViewResponse;
import dev.knalis.testing.dto.response.TestResponse;
import dev.knalis.testing.dto.response.TestStudentQuestionViewResponse;
import dev.knalis.testing.dto.response.TestStudentViewResponse;
import dev.knalis.testing.entity.Answer;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestGroupAvailability;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.TestHasAttemptsException;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.exception.TestNotArchivedException;
import dev.knalis.testing.factory.attempt.TestAttemptFactory;
import dev.knalis.testing.factory.test.TestFactory;
import dev.knalis.testing.mapper.TestMapper;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.TestGroupAvailabilityRepository;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.AnswerRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.repository.TestResultRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    
    @Mock
    private TestRepository testRepository;

    @Mock
    private TestAttemptRepository testAttemptRepository;

    @Mock
    private TestGroupAvailabilityRepository testGroupAvailabilityRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;

    @Mock
    private TestResultRepository testResultRepository;
    
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
                answerRepository,
                testResultRepository,
                new TestFactory(),
                new TestAttemptFactory(),
                testMapper,
                testingAuditService,
                testingEventPublisher,
                educationServiceClient,
                OBJECT_MAPPER
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
    void studentViewSerializedJsonDoesNotContainCorrectnessAndPreviewFalse() throws Exception {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Instant now = Instant.now();
        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setStatus(TestStatus.PUBLISHED);
        test.setTitle("Quiz");
        test.setShowCorrectAnswersAfterSubmit(true);
        test.setMaxPoints(100);
        TestGroupAvailability availability = new TestGroupAvailability();
        availability.setTestId(testId);
        availability.setGroupId(groupId);
        availability.setVisible(true);
        availability.setMaxAttempts(1);
        Question single = question(testId, QuestionType.SINGLE_CHOICE, 0);
        Question multiple = question(testId, QuestionType.MULTIPLE_CHOICE, 1);
        Question bool = question(testId, QuestionType.TRUE_FALSE, 2);
        Answer answer = answer(single.getId(), true, "A");
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(educationServiceClient.getGroupsByUser(userId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(testGroupAvailabilityRepository.findAvailableForTestAndGroups(eq(testId), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId))
                .thenReturn(List.of(single, multiple, bool));
        when(answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(any())).thenReturn(List.of(answer));
        TestStudentViewResponse view = testService.getStudentView(userId, testId);
        String json = OBJECT_MAPPER.writeValueAsString(view);
        assertFalse(json.contains("isCorrect"));
        assertFalse(view.preview());
    }

    @org.junit.jupiter.api.Test
    void previewViewSerializedJsonContainsCorrectnessAndPreviewTrue() throws Exception {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Instant now = Instant.now();
        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setStatus(TestStatus.PUBLISHED);
        test.setTitle("Quiz");
        test.setCreatedByUserId(teacherId);
        test.setMaxPoints(100);
        Question single = question(testId, QuestionType.SINGLE_CHOICE, 0);
        Answer answer = answer(single.getId(), true, "A");
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testMapper.toResponse(test)).thenReturn(new TestResponse(testId, topicId, "Quiz", 0, TestStatus.PUBLISHED, 1, 100, null, null, null, false, false, false, now, now));
        when(questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId)).thenReturn(List.of(single));
        when(answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(any())).thenReturn(List.of(answer));
        TestPreviewViewResponse view = testService.getPreviewView(teacherId, false, testId);
        String json = OBJECT_MAPPER.writeValueAsString(view);
        assertTrue(json.contains("isCorrect"));
        assertTrue(view.preview());
    }

    @org.junit.jupiter.api.Test
    void studentViewComplexTypesExposeSafePresentationOnly() throws Exception {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setStatus(TestStatus.PUBLISHED);
        test.setTitle("Quiz");
        test.setMaxPoints(100);

        TestGroupAvailability availability = new TestGroupAvailability();
        availability.setTestId(testId);
        availability.setGroupId(groupId);
        availability.setVisible(true);
        availability.setMaxAttempts(1);

        Question matching = question(testId, QuestionType.MATCHING, 0);
        matching.setConfigurationJson("{\"pairs\":[{\"left\":\"A\",\"right\":\"1\"},{\"left\":\"B\",\"right\":\"2\"}]}");
        Question ordering = question(testId, QuestionType.ORDERING, 1);
        ordering.setConfigurationJson("{\"items\":[\"First\",\"Second\",\"Third\"]}");
        Question blanks = question(testId, QuestionType.FILL_IN_THE_BLANK, 2);
        blanks.setConfigurationJson("{\"text\":\"A __ B __\",\"blanks\":[[\"x\"],[\"y\"]]}");
        Question numeric = question(testId, QuestionType.NUMERIC, 3);
        numeric.setConfigurationJson("{\"correctValue\":42,\"tolerance\":2,\"unit\":\"kg\"}");
        Question file = question(testId, QuestionType.FILE_ANSWER, 4);
        file.setConfigurationJson("{\"allowedFileTypes\":[\"application/pdf\"],\"maxFileSizeMb\":10,\"rubric\":\"secret\"}");
        Question manual = question(testId, QuestionType.MANUAL_GRADING, 5);
        manual.setConfigurationJson("{\"rubric\":\"teacher only\"}");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(educationServiceClient.getGroupsByUser(userId)).thenReturn(List.of(new GroupMembershipResponse(groupId)));
        when(testGroupAvailabilityRepository.findAvailableForTestAndGroups(eq(testId), any(), any(Instant.class)))
                .thenReturn(List.of(availability));
        when(questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId))
                .thenReturn(List.of(matching, ordering, blanks, numeric, file, manual));
        when(answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());

        TestStudentViewResponse view = testService.getStudentView(userId, testId);
        String json = OBJECT_MAPPER.writeValueAsString(view);
        assertFalse(json.contains("configurationJson"));
        assertFalse(json.contains("correctValue"));
        assertFalse(json.contains("tolerance"));
        assertFalse(json.contains("rubric"));
        assertFalse(json.contains("acceptedAnswers"));
        assertFalse(json.contains("isCorrect"));

        TestStudentQuestionViewResponse matchingView = view.questions().stream()
                .filter(question -> question.type() == QuestionType.MATCHING)
                .findFirst()
                .orElseThrow();
        ObjectNode presentation = (ObjectNode) OBJECT_MAPPER.readTree(matchingView.presentationJson());
        assertNotNull(presentation.path("leftItems"));
        assertNotNull(presentation.path("rightItems"));
    }

    private Question question(UUID testId, QuestionType type, int orderIndex) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setTestId(testId);
        question.setType(type);
        question.setText("Q");
        question.setPoints(1);
        question.setOrderIndex(orderIndex);
        question.setRequired(true);
        question.setCreatedAt(Instant.now());
        question.setUpdatedAt(Instant.now());
        return question;
    }

    private Answer answer(UUID questionId, boolean correct, String text) {
        Answer answer = new Answer();
        answer.setId(UUID.randomUUID());
        answer.setQuestionId(questionId);
        answer.setCorrect(correct);
        answer.setText(text);
        answer.setCreatedAt(Instant.now());
        answer.setUpdatedAt(Instant.now());
        return answer;
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

    @org.junit.jupiter.api.Test
    void closeAndReopenTestTransitionsStatus() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant now = Instant.now();

        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setTitle("Quiz 1");
        test.setOrderIndex(0);
        test.setStatus(TestStatus.PUBLISHED);
        test.setMaxAttempts(1);
        test.setMaxPoints(100);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testRepository.save(test)).thenReturn(test);
        when(testMapper.toResponse(test)).thenReturn(new TestResponse(
                testId,
                topicId,
                "Quiz 1",
                0,
                test.getStatus(),
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

        testService.closeTest(UUID.randomUUID(), true, testId);
        assertEquals(TestStatus.CLOSED, test.getStatus());
        testService.reopenTest(UUID.randomUUID(), true, testId);
        assertEquals(TestStatus.PUBLISHED, test.getStatus());
    }

    @org.junit.jupiter.api.Test
    void restoreTestRequiresArchivedStatus() {
        UUID testId = UUID.randomUUID();
        Test test = new Test();
        test.setId(testId);
        test.setStatus(TestStatus.DRAFT);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));

        assertThrows(
                TestNotArchivedException.class,
                () -> testService.restoreTest(UUID.randomUUID(), true, testId)
        );
    }

    @org.junit.jupiter.api.Test
    void deleteTestBlocksWhenAttemptsExist() {
        UUID testId = UUID.randomUUID();
        Test test = new Test();
        test.setId(testId);
        test.setStatus(TestStatus.ARCHIVED);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.countByTestId(testId)).thenReturn(1L);
        when(testResultRepository.countByTestId(testId)).thenReturn(0L);

        assertThrows(
                TestHasAttemptsException.class,
                () -> testService.deleteTest(UUID.randomUUID(), true, testId)
        );
    }
}
