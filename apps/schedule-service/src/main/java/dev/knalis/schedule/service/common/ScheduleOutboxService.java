package dev.knalis.schedule.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.schedule.config.ScheduleOutboxProperties;
import dev.knalis.schedule.entity.ScheduleOutboxEvent;
import dev.knalis.schedule.entity.ScheduleOutboxStatus;
import dev.knalis.schedule.repository.ScheduleOutboxRepository;
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
public class ScheduleOutboxService {
    
    private final ScheduleOutboxRepository scheduleOutboxRepository;
    private final ScheduleOutboxProperties scheduleOutboxProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public void enqueue(String topic, String messageKey, Object payload) {
        ScheduleOutboxEvent event = new ScheduleOutboxEvent();
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setEventType(payload.getClass().getSimpleName());
        event.setPayloadType(payload.getClass().getName());
        event.setPayloadJson(serialize(payload));
        event.setStatus(ScheduleOutboxStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        scheduleOutboxRepository.save(event);
        meterRegistry.counter("app.kafka.outbox.enqueued", "service", "schedule-service", "event", event.getEventType())
                .increment();
        log.info("Enqueued outbox event type={} topic={} outboxId={}", event.getEventType(), topic, event.getId());
    }
    
    @Transactional
    public List<ScheduleOutboxEvent> claimNextBatch(Instant now) {
        List<UUID> ids = scheduleOutboxRepository.lockNextBatchIds(now, scheduleOutboxProperties.getBatchSize());
        if (ids.isEmpty()) {
            return List.of();
        }
        
        List<ScheduleOutboxEvent> events = scheduleOutboxRepository.findAllById(ids);
        for (ScheduleOutboxEvent event : events) {
            event.setStatus(ScheduleOutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setAttemptCount(event.getAttemptCount() + 1);
        }
        scheduleOutboxRepository.saveAll(events);
        return events;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ScheduleOutboxEvent requireClaimedEvent(UUID eventId) {
        return scheduleOutboxRepository.findWithLockingById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId, int partition, long offset) {
        ScheduleOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(ScheduleOutboxStatus.PUBLISHED);
        event.setProcessingStartedAt(null);
        event.setPublishedAt(Instant.now());
        event.setPublishedPartition(partition);
        event.setPublishedOffset(offset);
        event.setLastError(null);
        scheduleOutboxRepository.save(event);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(UUID eventId, String errorMessage) {
        ScheduleOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(event.getAttemptCount() >= scheduleOutboxProperties.getMaxAttempts()
                ? ScheduleOutboxStatus.FAILED
                : ScheduleOutboxStatus.RETRY);
        event.setProcessingStartedAt(null);
        event.setNextAttemptAt(Instant.now().plus(backoffForAttempt(event.getAttemptCount())));
        event.setLastError(truncateError(errorMessage));
        scheduleOutboxRepository.save(event);
    }
    
    @Transactional
    public int recoverTimedOut(Instant now) {
        Instant cutoff = now.minus(scheduleOutboxProperties.getProcessingTimeout());
        return scheduleOutboxRepository.recoverTimedOut(
                ScheduleOutboxStatus.PROCESSING,
                ScheduleOutboxStatus.RETRY,
                cutoff,
                now
        );
    }
    
    @Transactional
    public int purgePublishedOlderThan(Instant cutoff) {
        return scheduleOutboxRepository.deletePublishedOlderThan(ScheduleOutboxStatus.PUBLISHED, cutoff);
    }
    
    public Object deserialize(ScheduleOutboxEvent event) {
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
        long computedMillis = Math.round(scheduleOutboxProperties.getInitialRetryDelay().toMillis() * multiplier);
        long cappedMillis = Math.min(computedMillis, scheduleOutboxProperties.getMaxRetryDelay().toMillis());
        return Duration.ofMillis(Math.max(cappedMillis, scheduleOutboxProperties.getInitialRetryDelay().toMillis()));
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
