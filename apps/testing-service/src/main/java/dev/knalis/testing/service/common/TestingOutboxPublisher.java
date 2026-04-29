package dev.knalis.testing.service.common;

import dev.knalis.testing.config.TestingOutboxProperties;
import dev.knalis.testing.entity.TestingOutboxEvent;
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
public class TestingOutboxPublisher {
    
    private final TestingOutboxService testingOutboxService;
    private final TestingOutboxProperties testingOutboxProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.publish-interval:2s}")
    public void publishPendingEvents() {
        if (!testingOutboxProperties.isEnabled()) {
            return;
        }
        
        Instant now = Instant.now();
        int recovered = testingOutboxService.recoverTimedOut(now);
        if (recovered > 0) {
            meterRegistry.counter("app.kafka.outbox.recovered", "service", "testing-service").increment(recovered);
            log.warn("Recovered timed out outbox events count={}", recovered);
        }
        
        List<TestingOutboxEvent> batch = testingOutboxService.claimNextBatch(now);
        for (TestingOutboxEvent event : batch) {
            publishClaimedEvent(event);
        }
    }
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.cleanup-interval:1h}")
    public void purgePublishedEvents() {
        if (!testingOutboxProperties.isEnabled()) {
            return;
        }
        
        int purged = testingOutboxService.purgePublishedOlderThan(Instant.now().minus(testingOutboxProperties.getPublishedRetention()));
        if (purged > 0) {
            meterRegistry.counter("app.kafka.outbox.purged", "service", "testing-service").increment(purged);
            log.info("Purged published outbox events count={}", purged);
        }
    }
    
    public void publishClaimedEvent(TestingOutboxEvent event) {
        try {
            Object payload = testingOutboxService.deserialize(event);
            SendResult<String, Object> result = kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload)
                    .get(testingOutboxProperties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            testingOutboxService.markPublished(
                    event.getId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
            meterRegistry.counter("app.kafka.outbox.publish.success", "service", "testing-service", "event", event.getEventType())
                    .increment();
            log.info("Published outbox event outboxId={} type={} topic={} partition={} offset={}",
                    event.getId(),
                    event.getEventType(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception exception) {
            testingOutboxService.markRetry(event.getId(), exception.getMessage());
            meterRegistry.counter("app.kafka.outbox.publish.failure", "service", "testing-service", "event", event.getEventType())
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
