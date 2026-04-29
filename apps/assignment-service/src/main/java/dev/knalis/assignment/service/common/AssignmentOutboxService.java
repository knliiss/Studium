package dev.knalis.assignment.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.assignment.config.AssignmentOutboxProperties;
import dev.knalis.assignment.entity.AssignmentOutboxEvent;
import dev.knalis.assignment.entity.AssignmentOutboxStatus;
import dev.knalis.assignment.repository.AssignmentOutboxRepository;
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
public class AssignmentOutboxService {
    
    private final AssignmentOutboxRepository assignmentOutboxRepository;
    private final AssignmentOutboxProperties assignmentOutboxProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public void enqueue(String topic, String messageKey, Object payload) {
        AssignmentOutboxEvent event = new AssignmentOutboxEvent();
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setEventType(payload.getClass().getSimpleName());
        event.setPayloadType(payload.getClass().getName());
        event.setPayloadJson(serialize(payload));
        event.setStatus(AssignmentOutboxStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        assignmentOutboxRepository.save(event);
        meterRegistry.counter("app.kafka.outbox.enqueued", "service", "assignment-service", "event", event.getEventType())
                .increment();
        log.info("Enqueued outbox event type={} topic={} outboxId={}", event.getEventType(), topic, event.getId());
    }
    
    @Transactional
    public List<AssignmentOutboxEvent> claimNextBatch(Instant now) {
        List<UUID> ids = assignmentOutboxRepository.lockNextBatchIds(now, assignmentOutboxProperties.getBatchSize());
        if (ids.isEmpty()) {
            return List.of();
        }
        
        List<AssignmentOutboxEvent> events = assignmentOutboxRepository.findAllById(ids);
        for (AssignmentOutboxEvent event : events) {
            event.setStatus(AssignmentOutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setAttemptCount(event.getAttemptCount() + 1);
        }
        assignmentOutboxRepository.saveAll(events);
        return events;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AssignmentOutboxEvent requireClaimedEvent(UUID eventId) {
        return assignmentOutboxRepository.findWithLockingById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId, int partition, long offset) {
        AssignmentOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(AssignmentOutboxStatus.PUBLISHED);
        event.setProcessingStartedAt(null);
        event.setPublishedAt(Instant.now());
        event.setPublishedPartition(partition);
        event.setPublishedOffset(offset);
        event.setLastError(null);
        assignmentOutboxRepository.save(event);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(UUID eventId, String errorMessage) {
        AssignmentOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(event.getAttemptCount() >= assignmentOutboxProperties.getMaxAttempts()
                ? AssignmentOutboxStatus.FAILED
                : AssignmentOutboxStatus.RETRY);
        event.setProcessingStartedAt(null);
        event.setNextAttemptAt(Instant.now().plus(backoffForAttempt(event.getAttemptCount())));
        event.setLastError(truncateError(errorMessage));
        assignmentOutboxRepository.save(event);
    }
    
    @Transactional
    public int recoverTimedOut(Instant now) {
        Instant cutoff = now.minus(assignmentOutboxProperties.getProcessingTimeout());
        return assignmentOutboxRepository.recoverTimedOut(
                AssignmentOutboxStatus.PROCESSING,
                AssignmentOutboxStatus.RETRY,
                cutoff,
                now
        );
    }
    
    @Transactional
    public int purgePublishedOlderThan(Instant cutoff) {
        return assignmentOutboxRepository.deletePublishedOlderThan(AssignmentOutboxStatus.PUBLISHED, cutoff);
    }
    
    public Object deserialize(AssignmentOutboxEvent event) {
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
        long computedMillis = Math.round(assignmentOutboxProperties.getInitialRetryDelay().toMillis() * multiplier);
        long cappedMillis = Math.min(computedMillis, assignmentOutboxProperties.getMaxRetryDelay().toMillis());
        return Duration.ofMillis(Math.max(cappedMillis, assignmentOutboxProperties.getInitialRetryDelay().toMillis()));
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
