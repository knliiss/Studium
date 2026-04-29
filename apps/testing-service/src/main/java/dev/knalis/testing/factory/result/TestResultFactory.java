package dev.knalis.testing.factory.result;

import dev.knalis.testing.entity.TestResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestResultFactory {
    
    public TestResult newTestResult(UUID testId, UUID userId, UUID attemptId, int score) {
        TestResult testResult = new TestResult();
        testResult.setTestId(testId);
        testResult.setUserId(userId);
        testResult.setAttemptId(attemptId);
        testResult.setScore(score);
        testResult.setAutoScore(score);
        return testResult;
    }
}
