package dev.knalis.testing.service.common;

import dev.knalis.contracts.event.TestCompletedEventV1;
import dev.knalis.contracts.event.TestPublishedEventV1;
import dev.knalis.contracts.event.TestStartedEventV1;
import dev.knalis.testing.config.KafkaTopicsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestingEventPublisher {
    
    private final TestingOutboxService testingOutboxService;
    private final KafkaTopicsProperties topics;
    
    public void publishTestPublished(TestPublishedEventV1 event) {
        testingOutboxService.enqueue(topics.getTestPublished(), event.testId().toString(), event);
    }
    
    public void publishTestStarted(TestStartedEventV1 event) {
        testingOutboxService.enqueue(topics.getTestStarted(), event.testId().toString(), event);
    }
    
    public void publishTestCompleted(TestCompletedEventV1 event) {
        testingOutboxService.enqueue(topics.getTestCompleted(), event.testId().toString(), event);
    }
}
