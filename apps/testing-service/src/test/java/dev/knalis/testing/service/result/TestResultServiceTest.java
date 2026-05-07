package dev.knalis.testing.service.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.client.education.dto.TopicResponse;
import dev.knalis.testing.dto.request.CreateTestResultRequest;
import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestAttempt;
import dev.knalis.testing.entity.TestResult;
import dev.knalis.testing.entity.TestStatus;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
}
