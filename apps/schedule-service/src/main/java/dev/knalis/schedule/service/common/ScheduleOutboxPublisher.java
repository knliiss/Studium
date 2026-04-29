package dev.knalis.schedule.service.common;

import dev.knalis.schedule.config.ScheduleOutboxProperties;
import dev.knalis.schedule.entity.ScheduleOutboxEvent;
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
public class ScheduleOutboxPublisher {
    
    private final ScheduleOutboxService scheduleOutboxService;
    private final ScheduleOutboxProperties scheduleOutboxProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.publish-interval:2s}")
    public void publishPendingEvents() {
        if (!scheduleOutboxProperties.isEnabled()) {
            return;
        }
        
        Instant now = Instant.now();
        int recovered = scheduleOutboxService.recoverTimedOut(now);
        if (recovered > 0) {
            meterRegistry.counter("app.kafka.outbox.recovered", "service", "schedule-service").increment(recovered);
            log.warn("Recovered timed out outbox events count={}", recovered);
        }
        
        List<ScheduleOutboxEvent> batch = scheduleOutboxService.claimNextBatch(now);
        for (ScheduleOutboxEvent event : batch) {
            publishClaimedEvent(event);
        }
    }
    
    @Scheduled(fixedDelayString = "${app.kafka.outbox.cleanup-interval:1h}")
    public void purgePublishedEvents() {
        if (!scheduleOutboxProperties.isEnabled()) {
            return;
        }
        
        int purged = scheduleOutboxService.purgePublishedOlderThan(Instant.now().minus(scheduleOutboxProperties.getPublishedRetention()));
        if (purged > 0) {
            meterRegistry.counter("app.kafka.outbox.purged", "service", "schedule-service").increment(purged);
            log.info("Purged published outbox events count={}", purged);
        }
    }
    
    public void publishClaimedEvent(ScheduleOutboxEvent event) {
        try {
            Object payload = scheduleOutboxService.deserialize(event);
            SendResult<String, Object> result = kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload)
                    .get(scheduleOutboxProperties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            scheduleOutboxService.markPublished(
                    event.getId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
            meterRegistry.counter("app.kafka.outbox.publish.success", "service", "schedule-service", "event", event.getEventType())
                    .increment();
            log.info("Published outbox event outboxId={} type={} topic={} partition={} offset={}",
                    event.getId(),
                    event.getEventType(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception exception) {
            scheduleOutboxService.markRetry(event.getId(), exception.getMessage());
            meterRegistry.counter("app.kafka.outbox.publish.failure", "service", "schedule-service", "event", event.getEventType())
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
