package dev.knalis.testing.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.testing.config.TestingOutboxProperties;
import dev.knalis.testing.entity.TestingOutboxEvent;
import dev.knalis.testing.entity.TestingOutboxStatus;
import dev.knalis.testing.repository.TestingOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestingOutboxService {
    
    private final TestingOutboxRepository testingOutboxRepository;
    private final TestingOutboxProperties testingOutboxProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public void enqueue(String topic, String messageKey, Object payload) {
        TestingOutboxEvent event = new TestingOutboxEvent();
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setEventType(payload.getClass().getSimpleName());
        event.setPayloadType(payload.getClass().getName());
        event.setPayloadJson(serialize(payload));
        event.setStatus(TestingOutboxStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        testingOutboxRepository.save(event);
        meterRegistry.counter("app.kafka.outbox.enqueued", "service", "testing-service", "event", event.getEventType())
                .increment();
        log.info("Enqueued outbox event type={} topic={} outboxId={}", event.getEventType(), topic, event.getId());
    }
    
    @Transactional
    public List<TestingOutboxEvent> claimNextBatch(Instant now) {
        List<UUID> ids = testingOutboxRepository.lockNextBatchIds(now, testingOutboxProperties.getBatchSize());
        if (ids.isEmpty()) {
            return List.of();
        }
        
        List<TestingOutboxEvent> events = testingOutboxRepository.findAllById(ids);
        for (TestingOutboxEvent event : events) {
            event.setStatus(TestingOutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setAttemptCount(event.getAttemptCount() + 1);
        }
        testingOutboxRepository.saveAll(events);
        return events;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TestingOutboxEvent requireClaimedEvent(UUID eventId) {
        return testingOutboxRepository.findWithLockingById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId, int partition, long offset) {
        TestingOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(TestingOutboxStatus.PUBLISHED);
        event.setProcessingStartedAt(null);
        event.setPublishedAt(Instant.now());
        event.setPublishedPartition(partition);
        event.setPublishedOffset(offset);
        event.setLastError(null);
        testingOutboxRepository.save(event);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(UUID eventId, String errorMessage) {
        TestingOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(event.getAttemptCount() >= testingOutboxProperties.getMaxAttempts()
                ? TestingOutboxStatus.FAILED
                : TestingOutboxStatus.RETRY);
        event.setProcessingStartedAt(null);
        event.setNextAttemptAt(Instant.now().plus(backoffForAttempt(event.getAttemptCount())));
        event.setLastError(truncateError(errorMessage));
        testingOutboxRepository.save(event);
    }
    
    @Transactional
    public int recoverTimedOut(Instant now) {
        Instant cutoff = now.minus(testingOutboxProperties.getProcessingTimeout());
        return testingOutboxRepository.recoverTimedOut(
                TestingOutboxStatus.PROCESSING,
                TestingOutboxStatus.RETRY,
                cutoff,
                now
        );
    }
    
    @Transactional
    public int purgePublishedOlderThan(Instant cutoff) {
        return testingOutboxRepository.deletePublishedOlderThan(TestingOutboxStatus.PUBLISHED, cutoff);
    }
    
    public Object deserialize(TestingOutboxEvent event) {
        try {
            Class<?> payloadType = Class.forName(event.getPayloadType());
            return objectMapper.readValue(event.getPayloadJson(), payloadType);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize outbox payload type=" + event.getPayloadType(), exception);
        }
    }
    
    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbox payload type=" + payload.getClass().getName(), exception);
        }
    }
    
    private Duration backoffForAttempt(int attemptCount) {
        double multiplier = Math.pow(2.0, Math.max(attemptCount - 1, 0));
        long computedMillis = Math.round(testingOutboxProperties.getInitialRetryDelay().toMillis() * multiplier);
        long cappedMillis = Math.min(computedMillis, testingOutboxProperties.getMaxRetryDelay().toMillis());
        return Duration.ofMillis(Math.max(cappedMillis, testingOutboxProperties.getInitialRetryDelay().toMillis()));
    }
    
    private String truncateError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() <= 2000
                ? errorMessage
                : errorMessage.substring(0, 2000);
    }
}
