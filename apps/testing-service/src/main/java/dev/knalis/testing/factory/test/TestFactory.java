package dev.knalis.testing.factory.test;

import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class TestFactory {
    
    public Test newTest(
            UUID topicId,
            String title,
            TestStatus status,
            int maxAttempts,
            int maxPoints,
            Integer timeLimitMinutes,
            Instant availableFrom,
            Instant availableUntil,
            boolean showCorrectAnswersAfterSubmit,
            boolean shuffleQuestions,
            boolean shuffleAnswers,
            int orderIndex
    ) {
        Test test = new Test();
        test.setTopicId(topicId);
        test.setTitle(title.trim());
        test.setStatus(status);
        test.setMaxAttempts(maxAttempts);
        test.setMaxPoints(maxPoints);
        test.setTimeLimitMinutes(timeLimitMinutes);
        test.setAvailableFrom(availableFrom);
        test.setAvailableUntil(availableUntil);
        test.setShowCorrectAnswersAfterSubmit(showCorrectAnswersAfterSubmit);
        test.setShuffleQuestions(shuffleQuestions);
        test.setShuffleAnswers(shuffleAnswers);
        test.setOrderIndex(orderIndex);
        return test;
    }
}
