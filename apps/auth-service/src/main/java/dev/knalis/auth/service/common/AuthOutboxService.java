package dev.knalis.auth.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.auth.config.AuthOutboxProperties;
import dev.knalis.auth.entity.AuthOutboxEvent;
import dev.knalis.auth.entity.AuthOutboxStatus;
import dev.knalis.auth.repository.AuthOutboxRepository;
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
public class AuthOutboxService {
    
    private final AuthOutboxRepository authOutboxRepository;
    private final AuthOutboxProperties authOutboxProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public void enqueue(String topic, String messageKey, Object payload) {
        AuthOutboxEvent event = new AuthOutboxEvent();
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setEventType(payload.getClass().getSimpleName());
        event.setPayloadType(payload.getClass().getName());
        event.setPayloadJson(serialize(payload));
        event.setStatus(AuthOutboxStatus.PENDING);
        event.setNextAttemptAt(Instant.now());
        authOutboxRepository.save(event);
        meterRegistry.counter("app.kafka.outbox.enqueued", "service", "auth-service", "event", event.getEventType())
                .increment();
        log.info("Enqueued outbox event type={} topic={} outboxId={}", event.getEventType(), topic, event.getId());
    }
    
    @Transactional
    public List<AuthOutboxEvent> claimNextBatch(Instant now) {
        List<UUID> ids = authOutboxRepository.lockNextBatchIds(now, authOutboxProperties.getBatchSize());
        if (ids.isEmpty()) {
            return List.of();
        }
        
        List<AuthOutboxEvent> events = authOutboxRepository.findAllById(ids);
        for (AuthOutboxEvent event : events) {
            event.setStatus(AuthOutboxStatus.PROCESSING);
            event.setProcessingStartedAt(now);
            event.setAttemptCount(event.getAttemptCount() + 1);
        }
        authOutboxRepository.saveAll(events);
        return events;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthOutboxEvent requireClaimedEvent(UUID eventId) {
        return authOutboxRepository.findWithLockingById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + eventId));
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId, int partition, long offset) {
        AuthOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(AuthOutboxStatus.PUBLISHED);
        event.setProcessingStartedAt(null);
        event.setPublishedAt(Instant.now());
        event.setPublishedPartition(partition);
        event.setPublishedOffset(offset);
        event.setLastError(null);
        authOutboxRepository.save(event);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(UUID eventId, String errorMessage) {
        AuthOutboxEvent event = requireClaimedEvent(eventId);
        event.setStatus(event.getAttemptCount() >= authOutboxProperties.getMaxAttempts()
                ? AuthOutboxStatus.FAILED
                : AuthOutboxStatus.RETRY);
        event.setProcessingStartedAt(null);
        event.setNextAttemptAt(Instant.now().plus(backoffForAttempt(event.getAttemptCount())));
        event.setLastError(truncateError(errorMessage));
        authOutboxRepository.save(event);
    }
    
    @Transactional
    public int recoverTimedOut(Instant now) {
        Instant cutoff = now.minus(authOutboxProperties.getProcessingTimeout());
        return authOutboxRepository.recoverTimedOut(
                AuthOutboxStatus.PROCESSING,
                AuthOutboxStatus.RETRY,
                cutoff,
                now
        );
    }
    
    @Transactional
    public int purgePublishedOlderThan(Instant cutoff) {
        return authOutboxRepository.deletePublishedOlderThan(AuthOutboxStatus.PUBLISHED, cutoff);
    }
    
    public Object deserialize(AuthOutboxEvent event) {
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
        long computedMillis = Math.round(authOutboxProperties.getInitialRetryDelay().toMillis() * multiplier);
        long cappedMillis = Math.min(computedMillis, authOutboxProperties.getMaxRetryDelay().toMillis());
        return Duration.ofMillis(Math.max(cappedMillis, authOutboxProperties.getInitialRetryDelay().toMillis()));
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
