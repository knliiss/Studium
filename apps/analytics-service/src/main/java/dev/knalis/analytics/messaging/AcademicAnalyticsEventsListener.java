package dev.knalis.analytics.messaging;

import dev.knalis.analytics.service.AnalyticsEventService;
import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.AssignmentOpenedEventV1;
import dev.knalis.contracts.event.AssignmentSubmittedEventV1;
import dev.knalis.contracts.event.DeadlineMissedEventV1;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import dev.knalis.contracts.event.LectureOpenedEventV1;
import dev.knalis.contracts.event.TestCompletedEventV1;
import dev.knalis.contracts.event.TestPublishedEventV1;
import dev.knalis.contracts.event.TestStartedEventV1;
import dev.knalis.contracts.event.TopicOpenedEventV1;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AcademicAnalyticsEventsListener {
    
    private final AnalyticsEventService analyticsEventService;
    private final MeterRegistry meterRegistry;
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.lecture-opened}", groupId = "${spring.application.name}")
    public void onLectureOpened(LectureOpenedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("LectureOpenedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.topic-opened}", groupId = "${spring.application.name}")
    public void onTopicOpened(TopicOpenedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("TopicOpenedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.assignment-opened}", groupId = "${spring.application.name}")
    public void onAssignmentOpened(AssignmentOpenedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("AssignmentOpenedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.assignment-submitted}", groupId = "${spring.application.name}")
    public void onAssignmentSubmitted(AssignmentSubmittedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("AssignmentSubmittedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.deadline-missed}", groupId = "${spring.application.name}")
    public void onDeadlineMissed(DeadlineMissedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("DeadlineMissedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.test-started}", groupId = "${spring.application.name}")
    public void onTestStarted(TestStartedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("TestStartedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.test-completed}", groupId = "${spring.application.name}")
    public void onTestCompleted(TestCompletedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("TestCompletedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.assignment-created}", groupId = "${spring.application.name}")
    public void onAssignmentCreated(AssignmentCreatedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("AssignmentCreatedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.grade-assigned}", groupId = "${spring.application.name}")
    public void onGradeAssigned(GradeAssignedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("GradeAssignedEventV1");
    }
    
    @KafkaListener(topics = "${app.analytics.kafka.topics.test-published}", groupId = "${spring.application.name}")
    public void onTestPublished(TestPublishedEventV1 event) {
        analyticsEventService.handle(event);
        countProcessed("TestPublishedEventV1");
    }
    
    private void countProcessed(String eventName) {
        meterRegistry.counter("app.kafka.event.processed", "service", "analytics-service", "event", eventName)
                .increment();
    }
}
