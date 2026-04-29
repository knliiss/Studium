package dev.knalis.testing.service.result;

import dev.knalis.testing.client.education.EducationServiceClient;
import dev.knalis.testing.dto.request.CreateTestResultRequest;
import dev.knalis.testing.dto.request.OverrideTestResultScoreRequest;
import dev.knalis.testing.dto.response.TestResultResponse;
import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestAttempt;
import dev.knalis.testing.entity.TestResult;
import dev.knalis.testing.entity.TestStatus;
import dev.knalis.testing.exception.TestInvalidStateException;
import dev.knalis.testing.exception.TestAccessDeniedException;
import dev.knalis.testing.exception.TestNotFoundException;
import dev.knalis.testing.exception.TestNotAvailableException;
import dev.knalis.testing.exception.TestTimeExpiredException;
import dev.knalis.testing.factory.result.TestResultFactory;
import dev.knalis.testing.mapper.TestResultMapper;
import dev.knalis.testing.repository.TestAttemptRepository;
import dev.knalis.testing.repository.TestRepository;
import dev.knalis.testing.repository.TestResultRepository;
import dev.knalis.testing.service.common.TestingAuditService;
import dev.knalis.testing.service.common.TestingEventPublisher;
import dev.knalis.contracts.event.TestCompletedEventV1;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TestResultService {
    
    private final TestResultRepository testResultRepository;
    private final TestRepository testRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final TestResultFactory testResultFactory;
    private final TestResultMapper testResultMapper;
    private final TestingEventPublisher testingEventPublisher;
    private final EducationServiceClient educationServiceClient;
    private final TestingAuditService testingAuditService;
    
    @Transactional
    public TestResultResponse createTestResult(UUID userId, CreateTestResultRequest request) {
        Test test = testRepository.findById(request.testId())
                .orElseThrow(() -> new TestNotFoundException(request.testId()));
        Instant now = Instant.now();
        if (test.getStatus() != TestStatus.PUBLISHED) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Only published tests can be submitted");
        }
        if (test.getAvailableUntil() != null && now.isAfter(test.getAvailableUntil())) {
            throw new TestNotAvailableException(test.getId(), test.getAvailableFrom(), test.getAvailableUntil());
        }
        if (request.score() > test.getMaxPoints()) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Result score cannot exceed test max points");
        }
        TestAttempt attempt = testAttemptRepository.findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(
                        request.testId(),
                        userId
                )
                .orElseThrow(() -> new TestInvalidStateException(
                        test.getId(),
                        test.getStatus(),
                        "Test result submission requires an active test attempt"
                ));
        if (test.getTimeLimitMinutes() != null
                && now.isAfter(attempt.getStartedAt().plusSeconds(test.getTimeLimitMinutes().longValue() * 60L))) {
            attempt.setCompletedAt(now);
            testAttemptRepository.save(attempt);
            throw new TestTimeExpiredException(test.getId(), attempt.getId(), attempt.getStartedAt(), test.getTimeLimitMinutes());
        }
        
        TestResult testResult = testResultFactory.newTestResult(request.testId(), userId, attempt.getId(), request.score());
        TestResult savedTestResult = testResultRepository.save(testResult);
        attempt.setCompletedAt(now);
        testAttemptRepository.save(attempt);
        UUID subjectId = educationServiceClient.getTopic(test.getTopicId()).subjectId();
        testingEventPublisher.publishTestCompleted(new TestCompletedEventV1(
                UUID.randomUUID(),
                savedTestResult.getCreatedAt(),
                userId,
                test.getId(),
                subjectId,
                test.getTopicId(),
                savedTestResult.getScore(),
                test.getMaxPoints(),
                savedTestResult.getCreatedAt()
        ));
        return testResultMapper.toResponse(savedTestResult);
    }

    @Transactional(readOnly = true)
    public TestResultResponse getTestResult(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID resultId
    ) {
        TestResult testResult = testResultRepository.findById(resultId)
                .orElseThrow(() -> new TestNotFoundException(resultId));
        Test test = testRepository.findById(testResult.getTestId())
                .orElseThrow(() -> new TestNotFoundException(testResult.getTestId()));
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        return testResultMapper.toResponse(testResult);
    }

    @Transactional
    public TestResultResponse overrideTestResultScore(
            UUID currentUserId,
            boolean privilegedAccess,
            UUID resultId,
            OverrideTestResultScoreRequest request
    ) {
        TestResult testResult = testResultRepository.findById(resultId)
                .orElseThrow(() -> new TestNotFoundException(resultId));
        Test test = testRepository.findById(testResult.getTestId())
                .orElseThrow(() -> new TestNotFoundException(testResult.getTestId()));
        assertTeacherOwnership(test, currentUserId, privilegedAccess);
        if (request.score() > test.getMaxPoints()) {
            throw new TestInvalidStateException(test.getId(), test.getStatus(), "Override score cannot exceed test max points");
        }
        TestResultResponse oldValue = testResultMapper.toResponse(testResult);

        testResult.setScore(request.score());
        testResult.setManualOverrideScore(request.score());
        testResult.setManualOverrideReason(request.reason() == null || request.reason().isBlank()
                ? null
                : request.reason().trim());
        testResult.setReviewedByUserId(currentUserId);
        testResult.setReviewedAt(Instant.now());

        TestResult savedTestResult = testResultRepository.save(testResult);
        TestResultResponse response = testResultMapper.toResponse(savedTestResult);
        testingAuditService.record(currentUserId, "TEST_RESULT_SCORE_OVERRIDDEN", "TEST_RESULT", response.id(), oldValue, response);
        return response;
    }

    private void assertTeacherOwnership(Test test, UUID currentUserId, boolean privilegedAccess) {
        if (privilegedAccess) {
            return;
        }
        if (test.getCreatedByUserId() != null && test.getCreatedByUserId().equals(currentUserId)) {
            return;
        }
        throw new TestAccessDeniedException(test.getId(), currentUserId);
    }
}
