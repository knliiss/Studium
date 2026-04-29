package dev.knalis.assignment.service.common;

import dev.knalis.assignment.config.AssignmentOutboxProperties;
import dev.knalis.assignment.entity.AssignmentOutboxEvent;
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
public class AssignmentOutboxPublisher {
    
    private final AssignmentOutboxService assignmentOutboxService;
    private final AssignmentOutboxProperties assignmentOutboxProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.publish-interval:2s}")
    public void publishPendingEvents() {
        if (!assignmentOutboxProperties.isEnabled()) {
            return;
        }
        
        Instant now = Instant.now();
        int recovered = assignmentOutboxService.recoverTimedOut(now);
        if (recovered > 0) {
            meterRegistry.counter("app.kafka.outbox.recovered", "service", "assignment-service").increment(recovered);
            log.warn("Recovered timed out outbox events count={}", recovered);
        }
        
        List<AssignmentOutboxEvent> batch = assignmentOutboxService.claimNextBatch(now);
        for (AssignmentOutboxEvent event : batch) {
            publishClaimedEvent(event);
        }
    }
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.cleanup-interval:1h}")
    public void purgePublishedEvents() {
        if (!assignmentOutboxProperties.isEnabled()) {
            return;
        }
        
        int purged = assignmentOutboxService.purgePublishedOlderThan(Instant.now().minus(assignmentOutboxProperties.getPublishedRetention()));
        if (purged > 0) {
            meterRegistry.counter("app.kafka.outbox.purged", "service", "assignment-service").increment(purged);
            log.info("Purged published outbox events count={}", purged);
        }
    }
    
    public void publishClaimedEvent(AssignmentOutboxEvent event) {
        try {
            Object payload = assignmentOutboxService.deserialize(event);
            SendResult<String, Object> result = kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload)
                    .get(assignmentOutboxProperties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            assignmentOutboxService.markPublished(
                    event.getId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
            meterRegistry.counter("app.kafka.outbox.publish.success", "service", "assignment-service", "event", event.getEventType())
                    .increment();
            log.info("Published outbox event outboxId={} type={} topic={} partition={} offset={}",
                    event.getId(),
                    event.getEventType(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception exception) {
            assignmentOutboxService.markRetry(event.getId(), exception.getMessage());
            meterRegistry.counter("app.kafka.outbox.publish.failure", "service", "assignment-service", "event", event.getEventType())
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
