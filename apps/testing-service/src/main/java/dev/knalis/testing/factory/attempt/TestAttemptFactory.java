package dev.knalis.testing.factory.attempt;

import dev.knalis.testing.entity.TestAttempt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestAttemptFactory {

    public TestAttempt newAttempt(UUID testId, UUID userId) {
        TestAttempt attempt = new TestAttempt();
        attempt.setTestId(testId);
        attempt.setUserId(userId);
        return attempt;
    }
}
