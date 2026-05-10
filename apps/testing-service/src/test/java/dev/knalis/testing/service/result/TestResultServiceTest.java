package dev.knalis.testing.service.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.client.education.dto.TopicResponse;
import dev.knalis.testing.dto.request.CreateTestResultRequest;
import dev.knalis.testing.dto.request.QuestionAnswerSubmissionRequest;
import dev.knalis.testing.dto.request.SubmitTestAttemptRequest;
import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.entity.Answer;
import dev.knalis.testing.entity.Question;
import dev.knalis.testing.entity.QuestionType;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestAttempt;
import dev.knalis.testing.entity.TestResult;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.ActiveTestAttemptNotFoundException;
import dev.knalis.testing.exception.TestNotFoundException;
import dev.knalis.testing.factory.attempt.TestAttemptFactory;
import dev.knalis.testing.factory.result.TestResultFactory;
import dev.knalis.testing.mapper.TestResultMapper;
import dev.knalis.testing.repository.AnswerRepository;
import dev.knalis.testing.repository.QuestionRepository;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.TestResultRepository;
import dev.knalis.testing.service.common.TestingAuditService;
import dev.knalis.testing.service.common.TestingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestResultServiceTest {
    
    @Mock
    private TestResultRepository testResultRepository;
    
    @Mock
    private TestRepository testRepository;

    @Mock
    private TestAttemptRepository testAttemptRepository;
    
    @Mock
    private TestResultMapper testResultMapper;
    
    @Mock
    private TestingEventPublisher testingEventPublisher;

    @Mock
    private TestingAuditService testingAuditService;
    
    @Mock
    private EducationServiceClient educationServiceClient;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRepository answerRepository;
    
    private TestResultService testResultService;
    
    @BeforeEach
    void setUp() {
        testResultService = new TestResultService(
                testResultRepository,
                testRepository,
                testAttemptRepository,
                new TestResultFactory(),
                testResultMapper,
                testingEventPublisher,
                educationServiceClient,
                testingAuditService,
                questionRepository,
                answerRepository,
                new ObjectMapper()
        );
    }
    
    @org.junit.jupiter.api.Test
    void createTestResultThrowsWhenTestIsMissing() {
        UUID testId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        when(testRepository.findById(testId)).thenReturn(Optional.empty());
        
        assertThrows(
                TestNotFoundException.class,
                () -> testResultService.createTestResult(userId, new CreateTestResultRequest(testId, 80))
        );
    }
    
    @org.junit.jupiter.api.Test
    void createTestResultSavesResult() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        Instant now = Instant.now();
        
        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setTitle("Quiz");
        test.setStatus(TestStatus.PUBLISHED);
        test.setMaxAttempts(1);
        test.setMaxPoints(100);

        TestAttempt attempt = new TestAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setTestId(testId);
        attempt.setUserId(userId);
        attempt.setStartedAt(now);

        TestResult testResult = new TestResult();
        testResult.setId(resultId);
        testResult.setTestId(testId);
        testResult.setUserId(userId);
        testResult.setAttemptId(attempt.getId());
        testResult.setScore(80);
        testResult.setAutoScore(80);
        testResult.setCreatedAt(now);
        testResult.setUpdatedAt(now);
        
        TestResultResponse response = new TestResultResponse(
                resultId,
                testId,
                userId,
                attempt.getId(),
                80,
                80,
                null,
                null,
                null,
                null,
                now,
                now
        );
        
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.of(attempt));
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId,
                subjectId,
                "Topic",
                1,
                now,
                now
        ));
        when(testResultRepository.save(any(TestResult.class))).thenReturn(testResult);
        when(testResultMapper.toResponse(testResult)).thenReturn(response);
        
        TestResultResponse result = testResultService.createTestResult(userId, new CreateTestResultRequest(testId, 80));
        
        assertEquals(response, result);
        verify(testingEventPublisher).publishTestCompleted(any());
    }

    @org.junit.jupiter.api.Test
    void submitTestAttemptScoresAllTypedContractsAndMalformedValuesSafely() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Test test = publishedTest(testId, topicId);
        TestAttempt attempt = activeAttempt(testId, userId, now);

        Question single = question(testId, QuestionType.SINGLE_CHOICE, 5, null);
        Question multi = question(testId, QuestionType.MULTIPLE_CHOICE, 5, null);
        Question bool = question(testId, QuestionType.TRUE_FALSE, 5, null);
        Question shortAnswer = question(testId, QuestionType.SHORT_ANSWER, 5, null);
        Question numeric = question(testId, QuestionType.NUMERIC, 5, "{\"correctValue\":10,\"tolerance\":0.5}");
        Question matching = question(testId, QuestionType.MATCHING, 5,
                "{\"leftItems\":[{\"id\":\"l1\",\"text\":\"A\"},{\"id\":\"l2\",\"text\":\"B\"}],\"rightItems\":[{\"id\":\"r1\",\"text\":\"1\"},{\"id\":\"r2\",\"text\":\"2\"}],\"pairs\":[{\"leftId\":\"l1\",\"rightId\":\"r1\"},{\"leftId\":\"l2\",\"rightId\":\"r2\"}]}");
        Question ordering = question(testId, QuestionType.ORDERING, 5,
                "{\"items\":[{\"id\":\"i1\",\"text\":\"First\",\"orderIndex\":0},{\"id\":\"i2\",\"text\":\"Second\",\"orderIndex\":1}]}");
        Question blanks = question(testId, QuestionType.FILL_IN_THE_BLANK, 5,
                "{\"text\":\"A __ B __\",\"blanks\":[{\"id\":\"b1\",\"acceptedAnswers\":[\"x\"]},{\"id\":\"b2\",\"acceptedAnswers\":[\"y\"]}]}");
        Question longText = question(testId, QuestionType.LONG_TEXT, 5, null);
        Question fileAnswer = question(testId, QuestionType.FILE_ANSWER, 5, null);
        Question manual = question(testId, QuestionType.MANUAL_GRADING, 5, null);

        Answer singleCorrect = answer(single.getId(), "single-ok", true);
        Answer multiCorrectA = answer(multi.getId(), "multi-a", true);
        Answer multiCorrectB = answer(multi.getId(), "multi-b", true);
        Answer boolCorrect = answer(bool.getId(), "TRUE", true);
        Answer shortCorrect = answer(shortAnswer.getId(), "alpha", true);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.of(attempt));
        when(questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId))
                .thenReturn(List.of(single, multi, bool, shortAnswer, numeric, matching, ordering, blanks, longText, fileAnswer, manual));
        when(answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(any())).thenReturn(List.of(
                singleCorrect, multiCorrectA, multiCorrectB, boolCorrect, shortCorrect
        ));
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(
                topicId, subjectId, "Topic", 1, now, now
        ));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(testResultMapper.toResponse(any(TestResult.class))).thenAnswer(invocation -> {
            TestResult saved = invocation.getArgument(0);
            return new TestResultResponse(saved.getId(), saved.getTestId(), saved.getUserId(), saved.getAttemptId(), saved.getScore(), saved.getAutoScore(), null, null, null, null, saved.getCreatedAt(), saved.getUpdatedAt());
        });

        SubmitTestAttemptRequest request = new SubmitTestAttemptRequest(List.of(
                submission(single.getId(), text(singleCorrect.getId().toString())),
                submission(multi.getId(), array(multiCorrectA.getId().toString(), multiCorrectB.getId().toString())),
                submission(bool.getId(), text(boolCorrect.getId().toString())),
                submission(shortAnswer.getId(), text(" alpha ")),
                submission(numeric.getId(), text("10.2")),
                submission(matching.getId(), object("l1", "r1", "l2", "r2")),
                submission(ordering.getId(), array("i1", "i2")),
                submission(blanks.getId(), object("b1", "x", "b2", "y")),
                submission(longText.getId(), text("manual")),
                submission(fileAnswer.getId(), text("file-ref")),
                submission(manual.getId(), object("body", "manual")),
                submission(UUID.randomUUID(), text("ignored")),
                submission(fileAnswer.getId(), object("broken", "shape"))
        ));

        TestResultResponse response = testResultService.submitTestAttempt(userId, testId, request);
        assertEquals(40, response.score());
    }

    @org.junit.jupiter.api.Test
    void submitTestAttemptSupportsLegacyMatchingOrderingAndFillInBlankFallback() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Test test = publishedTest(testId, topicId);
        TestAttempt attempt = activeAttempt(testId, userId, now);

        Question matching = question(testId, QuestionType.MATCHING, 5,
                "{\"pairs\":[{\"left\":\"A\",\"right\":\"1\"},{\"left\":\"B\",\"right\":\"2\"}]}");
        Question ordering = question(testId, QuestionType.ORDERING, 5, "{\"items\":[\"First\",\"Second\"]}");
        Question blanks = question(testId, QuestionType.FILL_IN_THE_BLANK, 5, "{\"blanks\":[[\"x\"],[\"y\"]]}");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.of(attempt));
        when(questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId))
                .thenReturn(List.of(matching, ordering, blanks));
        when(answerRepository.findAllByQuestionIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(topicId, subjectId, "Topic", 1, now, now));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(testResultMapper.toResponse(any(TestResult.class))).thenAnswer(invocation -> {
            TestResult saved = invocation.getArgument(0);
            return new TestResultResponse(saved.getId(), saved.getTestId(), saved.getUserId(), saved.getAttemptId(), saved.getScore(), saved.getAutoScore(), null, null, null, null, saved.getCreatedAt(), saved.getUpdatedAt());
        });

        SubmitTestAttemptRequest request = new SubmitTestAttemptRequest(List.of(
                submission(matching.getId(), object("0", "1", "1", "2")),
                submission(ordering.getId(), array("First", "Second")),
                submission(blanks.getId(), text("x\ny"))
        ));
        TestResultResponse response = testResultService.submitTestAttempt(userId, testId, request);
        assertEquals(15, response.score());
    }

    @org.junit.jupiter.api.Test
    void submitTestAttemptAfterTimeoutCreatesResultAndDoesNotThrow() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Test test = publishedTest(testId, topicId);
        test.setTimeLimitMinutes(1);
        TestAttempt attempt = activeAttempt(testId, userId, now.minusSeconds(120));

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.of(attempt));
        when(questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId)).thenReturn(List.of());
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(topicId, subjectId, "Topic", 1, now, now));
        when(testResultRepository.findFirstByAttemptId(attempt.getId())).thenReturn(Optional.empty());
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(testResultMapper.toResponse(any(TestResult.class))).thenAnswer(invocation -> {
            TestResult saved = invocation.getArgument(0);
            return new TestResultResponse(saved.getId(), saved.getTestId(), saved.getUserId(), saved.getAttemptId(), saved.getScore(), saved.getAutoScore(), null, null, null, null, saved.getCreatedAt(), saved.getUpdatedAt());
        });

        TestResultResponse response = testResultService.submitTestAttempt(userId, testId, new SubmitTestAttemptRequest(List.of()));
        assertEquals(attempt.getId(), response.attemptId());
        verify(testingEventPublisher).publishTestCompleted(any());
    }

    @org.junit.jupiter.api.Test
    void submitTestAttemptReturnsExistingResultForDuplicateFinish() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Test test = publishedTest(testId, topicId);
        TestAttempt attempt = activeAttempt(testId, userId, now);
        TestResult existing = new TestResult();
        existing.setId(UUID.randomUUID());
        existing.setAttemptId(attempt.getId());
        existing.setTestId(testId);
        existing.setUserId(userId);
        existing.setScore(10);
        existing.setAutoScore(10);
        TestResultResponse mapped = new TestResultResponse(existing.getId(), testId, userId, attempt.getId(), 10, 10, null, null, null, null, now, now);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.of(attempt));
        when(testResultRepository.findFirstByAttemptId(attempt.getId())).thenReturn(Optional.of(existing));
        when(testResultMapper.toResponse(existing)).thenReturn(mapped);

        TestResultResponse response = testResultService.submitTestAttempt(userId, testId, new SubmitTestAttemptRequest(List.of()));
        assertEquals(existing.getId(), response.id());
        verify(testResultRepository, never()).save(any(TestResult.class));
        verify(testingEventPublisher, never()).publishTestCompleted(any());
    }

    @org.junit.jupiter.api.Test
    void submitTestAttemptThrowsSpecificErrorWhenNoActiveOrRecoverableAttempt() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Test test = publishedTest(testId, topicId);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.empty());
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(testId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                ActiveTestAttemptNotFoundException.class,
                () -> testResultService.submitTestAttempt(userId, testId, new SubmitTestAttemptRequest(List.of()))
        );
    }

    @org.junit.jupiter.api.Test
    void submitTestAttemptRecoversCompletedAttemptWithoutResult() {
        UUID testId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Test test = publishedTest(testId, topicId);
        TestAttempt completedAttempt = activeAttempt(testId, userId, now.minusSeconds(120));
        completedAttempt.setCompletedAt(now.minusSeconds(60));

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(testId, userId))
                .thenReturn(Optional.empty());
        when(testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(testId, userId))
                .thenReturn(Optional.of(completedAttempt));
        when(testResultRepository.findFirstByAttemptId(completedAttempt.getId())).thenReturn(Optional.empty());
        when(questionRepository.findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(testId)).thenReturn(List.of());
        when(educationServiceClient.getTopic(topicId)).thenReturn(new TopicResponse(topicId, subjectId, "Topic", 1, now, now));
        when(testResultRepository.save(any(TestResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(testResultMapper.toResponse(any(TestResult.class))).thenAnswer(invocation -> {
            TestResult saved = invocation.getArgument(0);
            return new TestResultResponse(saved.getId(), saved.getTestId(), saved.getUserId(), saved.getAttemptId(), saved.getScore(), saved.getAutoScore(), null, null, null, null, saved.getCreatedAt(), saved.getUpdatedAt());
        });

        TestResultResponse response = testResultService.submitTestAttempt(userId, testId, new SubmitTestAttemptRequest(List.of()));
        assertEquals(completedAttempt.getId(), response.attemptId());
        verify(testResultRepository).save(any(TestResult.class));
    }

    private Test publishedTest(UUID testId, UUID topicId) {
        Test test = new Test();
        test.setId(testId);
        test.setTopicId(topicId);
        test.setTitle("Quiz");
        test.setStatus(TestStatus.PUBLISHED);
        test.setMaxAttempts(1);
        test.setMaxPoints(100);
        return test;
    }

    private TestAttempt activeAttempt(UUID testId, UUID userId, Instant startedAt) {
        TestAttempt attempt = new TestAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setTestId(testId);
        attempt.setUserId(userId);
        attempt.setStartedAt(startedAt);
        return attempt;
    }

    private Question question(UUID testId, QuestionType type, int points, String configurationJson) {
        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setTestId(testId);
        question.setType(type);
        question.setPoints(points);
        question.setOrderIndex(0);
        question.setText("Q");
        question.setRequired(true);
        question.setConfigurationJson(configurationJson);
        return question;
    }

    private Answer answer(UUID questionId, String text, boolean correct) {
        Answer answer = new Answer();
        answer.setId(UUID.randomUUID());
        answer.setQuestionId(questionId);
        answer.setText(text);
        answer.setCorrect(correct);
        return answer;
    }

    private QuestionAnswerSubmissionRequest submission(UUID questionId, JsonNode value) {
        return new QuestionAnswerSubmissionRequest(questionId, value);
    }

    private JsonNode text(String value) {
        return new ObjectMapper().getNodeFactory().textNode(value);
    }

    private JsonNode array(String... values) {
        ArrayNode array = new ObjectMapper().createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private JsonNode object(String key1, String value1, String key2, String value2) {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put(key1, value1);
        node.put(key2, value2);
        return node;
    }

    private JsonNode object(String key, String value) {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put(key, value);
        return node;
    }
}
