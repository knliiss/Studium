package dev.knalis.notification.messaging;

import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.AssignmentUpdatedEventV1;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import dev.knalis.contracts.event.ScheduleExtraLessonCreatedEventV1;
import dev.knalis.contracts.event.ScheduleLessonCancelledEventV1;
import dev.knalis.contracts.event.ScheduleLessonReplacedEventV1;
import dev.knalis.contracts.event.ScheduleOverrideCreatedEventV1;
import dev.knalis.contracts.event.TestPublishedEventV1;
import dev.knalis.notification.service.AcademicNotificationFactory;
import dev.knalis.notification.service.AcademicNotificationRecipientService;
import dev.knalis.notification.service.NotificationDraft;
import dev.knalis.notification.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AcademicNotificationEventsListener {
    
    private final NotificationService notificationService;
    private final AcademicNotificationFactory academicNotificationFactory;
    private final AcademicNotificationRecipientService academicNotificationRecipientService;
    private final MeterRegistry meterRegistry;
    
    @KafkaListener(topics = "${app.notification.kafka.topics.schedule-override-created}", groupId = "${spring.application.name}")
    public void onScheduleOverrideCreated(ScheduleOverrideCreatedEventV1 event) {
        handleDrafts(
                academicNotificationRecipientService.resolveScheduleRecipients(event.groupId(), event.teacherId()).stream()
                        .map(recipientUserId -> academicNotificationFactory.fromScheduleOverrideCreated(event, recipientUserId))
                        .toList()
        );
        countProcessed("ScheduleOverrideCreatedEventV1");
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.schedule-lesson-cancelled}", groupId = "${spring.application.name}")
    public void onScheduleLessonCancelled(ScheduleLessonCancelledEventV1 event) {
        handleDrafts(
                academicNotificationRecipientService.resolveScheduleRecipients(event.groupId(), event.teacherId()).stream()
                        .map(recipientUserId -> academicNotificationFactory.fromScheduleLessonCancelled(event, recipientUserId))
                        .toList()
        );
        countProcessed("ScheduleLessonCancelledEventV1");
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.schedule-lesson-replaced}", groupId = "${spring.application.name}")
    public void onScheduleLessonReplaced(ScheduleLessonReplacedEventV1 event) {
        handleDrafts(
                academicNotificationRecipientService.resolveScheduleRecipients(
                                event.groupId(),
                                event.oldTeacherId(),
                                event.newTeacherId()
                        ).stream()
                        .map(recipientUserId -> academicNotificationFactory.fromScheduleLessonReplaced(event, recipientUserId))
                        .toList()
        );
        countProcessed("ScheduleLessonReplacedEventV1");
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.schedule-extra-lesson-created}", groupId = "${spring.application.name}")
    public void onScheduleExtraLessonCreated(ScheduleExtraLessonCreatedEventV1 event) {
        handleDrafts(
                academicNotificationRecipientService.resolveScheduleRecipients(event.groupId(), event.teacherId()).stream()
                        .map(recipientUserId -> academicNotificationFactory.fromScheduleExtraLessonCreated(event, recipientUserId))
                        .toList()
        );
        countProcessed("ScheduleExtraLessonCreatedEventV1");
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.assignment-created}", groupId = "${spring.application.name}")
    public void onAssignmentCreated(AssignmentCreatedEventV1 event) {
        handleDrafts(
                academicNotificationRecipientService.resolveTopicStudentRecipients(event.topicId()).stream()
                        .map(recipientUserId -> academicNotificationFactory.fromAssignmentCreated(event, recipientUserId))
                        .toList()
        );
        countProcessed("AssignmentCreatedEventV1");
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.assignment-updated}", groupId = "${spring.application.name}")
    public void onAssignmentUpdated(AssignmentUpdatedEventV1 event) {
        handleDrafts(
                academicNotificationRecipientService.resolveTopicStudentRecipients(event.topicId()).stream()
                        .map(recipientUserId -> academicNotificationFactory.fromAssignmentUpdated(event, recipientUserId))
                        .toList()
        );
        countProcessed("AssignmentUpdatedEventV1");
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.grade-assigned}", groupId = "${spring.application.name}")
    public void onGradeAssigned(GradeAssignedEventV1 event) {
        handleDraft(academicNotificationFactory.fromGradeAssigned(event));
        countProcessed("GradeAssignedEventV1");
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.test-published}", groupId = "${spring.application.name}")
    public void onTestPublished(TestPublishedEventV1 event) {
        handleDrafts(
                academicNotificationRecipientService.resolveTopicStudentRecipients(event.topicId()).stream()
                        .map(recipientUserId -> academicNotificationFactory.fromTestPublished(event, recipientUserId))
                        .toList()
        );
        countProcessed("TestPublishedEventV1");
    }
    
    private void handleDrafts(List<NotificationDraft> drafts) {
        for (NotificationDraft draft : drafts) {
            handleDraft(draft);
        }
    }
    
    private void handleDraft(NotificationDraft draft) {
        log.info("Creating notification from {} for userId={}", draft.sourceEventType(), draft.userId());
        notificationService.createFromEvent(
                draft.userId(),
                draft.sourceEventId(),
                draft.sourceEventType(),
                draft.type(),
                draft.category(),
                draft.title(),
                draft.body(),
                draft.payload()
        );
    }
    
    private void countProcessed(String eventName) {
        meterRegistry.counter("app.kafka.event.processed", "service", "notification-service", "event", eventName)
                .increment();
    }
}
