package dev.knalis.schedule.service.common;

import dev.knalis.contracts.event.ScheduleExtraLessonCreatedEventV1;
import dev.knalis.contracts.event.ScheduleLessonCancelledEventV1;
import dev.knalis.contracts.event.ScheduleLessonReplacedEventV1;
import dev.knalis.contracts.event.ScheduleOverrideCreatedEventV1;
import dev.knalis.schedule.config.KafkaTopicsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleEventPublisher {
    
    private final ScheduleOutboxService scheduleOutboxService;
    private final KafkaTopicsProperties topics;
    
    public void publishScheduleOverrideCreated(ScheduleOverrideCreatedEventV1 event) {
        scheduleOutboxService.enqueue(topics.getScheduleOverrideCreated(), event.overrideId().toString(), event);
    }
    
    public void publishScheduleLessonCancelled(ScheduleLessonCancelledEventV1 event) {
        scheduleOutboxService.enqueue(topics.getScheduleLessonCancelled(), event.overrideId().toString(), event);
    }
    
    public void publishScheduleLessonReplaced(ScheduleLessonReplacedEventV1 event) {
        scheduleOutboxService.enqueue(topics.getScheduleLessonReplaced(), event.overrideId().toString(), event);
    }
    
    public void publishScheduleExtraLessonCreated(ScheduleExtraLessonCreatedEventV1 event) {
        scheduleOutboxService.enqueue(topics.getScheduleExtraLessonCreated(), event.overrideId().toString(), event);
    }
}
