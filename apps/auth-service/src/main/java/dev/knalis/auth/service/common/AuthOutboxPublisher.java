package dev.knalis.auth.service.common;

import dev.knalis.auth.config.AuthOutboxProperties;
import dev.knalis.auth.entity.AuthOutboxEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthOutboxPublisher {
    
    private final AuthOutboxService authOutboxService;
    private final AuthOutboxProperties authOutboxProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.publish-interval:2s}")
    public void publishPendingEvents() {
        if (!authOutboxProperties.isEnabled()) {
            return;
        }
        
        Instant now = Instant.now();
        int recovered = authOutboxService.recoverTimedOut(now);
        if (recovered > 0) {
            meterRegistry.counter("app.kafka.outbox.recovered", "service", "auth-service").increment(recovered);
            log.warn("Recovered timed out outbox events count={}", recovered);
        }
        
        List<AuthOutboxEvent> batch = authOutboxService.claimNextBatch(now);
        for (AuthOutboxEvent event : batch) {
            publishClaimedEvent(event);
        }
    }
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.cleanup-interval:1h}")
    public void purgePublishedEvents() {
        if (!authOutboxProperties.isEnabled()) {
            return;
        }
        
        int purged = authOutboxService.purgePublishedOlderThan(Instant.now().minus(authOutboxProperties.getPublishedRetention()));
        if (purged > 0) {
            meterRegistry.counter("app.kafka.outbox.purged", "service", "auth-service").increment(purged);
            log.info("Purged published outbox events count={}", purged);
        }
    }
    
    public void publishClaimedEvent(AuthOutboxEvent event) {
        try {
            Object payload = authOutboxService.deserialize(event);
            SendResult<String, Object> result = kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload)
                    .get(authOutboxProperties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            authOutboxService.markPublished(
                    event.getId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
            meterRegistry.counter("app.kafka.outbox.publish.success", "service", "auth-service", "event", event.getEventType())
                    .increment();
            log.info("Published outbox event outboxId={} type={} topic={} partition={} offset={}",
                    event.getId(),
                    event.getEventType(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception exception) {
            authOutboxService.markRetry(event.getId(), exception.getMessage());
            meterRegistry.counter("app.kafka.outbox.publish.failure", "service", "auth-service", "event", event.getEventType())
                    .increment();
            log.error("Failed to publish outbox event outboxId={} type={} topic={} message={}",
                    event.getId(),
                    event.getEventType(),
                    event.getTopic(),
                    exception.getMessage(),
                    exception);
        }
    }
}
