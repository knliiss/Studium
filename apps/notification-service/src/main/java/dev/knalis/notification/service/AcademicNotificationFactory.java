package dev.knalis.notification.service;

import dev.knalis.contracts.event.AssignmentCreatedEventV1;
import dev.knalis.contracts.event.AssignmentUpdatedEventV1;
import dev.knalis.contracts.event.GradeAssignedEventV1;
import dev.knalis.contracts.event.ScheduleExtraLessonCreatedEventV1;
import dev.knalis.contracts.event.ScheduleLessonCancelledEventV1;
import dev.knalis.contracts.event.ScheduleLessonFormatV1;
import dev.knalis.contracts.event.ScheduleLessonReplacedEventV1;
import dev.knalis.contracts.event.ScheduleLessonTypeV1;
import dev.knalis.contracts.event.ScheduleOverrideCreatedEventV1;
import dev.knalis.contracts.event.ScheduleOverrideTypeV1;
import dev.knalis.contracts.event.TestPublishedEventV1;
import dev.knalis.notification.entity.NotificationCategory;
import dev.knalis.notification.entity.NotificationType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class AcademicNotificationFactory {
    
    private final NotificationEventIdFactory notificationEventIdFactory;
    
    public AcademicNotificationFactory(NotificationEventIdFactory notificationEventIdFactory) {
        this.notificationEventIdFactory = notificationEventIdFactory;
    }
    
    public NotificationDraft fromScheduleOverrideCreated(ScheduleOverrideCreatedEventV1 event, UUID recipientUserId) {
        NotificationType type = NotificationType.SCHEDULE_LESSON_CHANGED;
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forRecipient(event.eventId(), recipientUserId, type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.SCHEDULE,
                "Schedule changed",
                scheduleChangedBody(event.lessonType(), event.date()),
                schedulePayload(
                        event.eventId(),
                        "SCHEDULE_OVERRIDE",
                        event.overrideId(),
                        event.date(),
                        event.groupId(),
                        event.subjectId(),
                        event.teacherId(),
                        event.slotId(),
                        event.roomId(),
                        event.lessonType(),
                        event.lessonFormat(),
                        event.onlineMeetingUrl(),
                        event.notes()
                )
        );
    }
    
    public NotificationDraft fromScheduleLessonCancelled(ScheduleLessonCancelledEventV1 event, UUID recipientUserId) {
        NotificationType type = NotificationType.SCHEDULE_LESSON_CANCELLED;
        Map<String, Object> payload = new LinkedHashMap<>(schedulePayload(
                event.eventId(),
                "SCHEDULE_OVERRIDE",
                event.overrideId(),
                event.date(),
                event.groupId(),
                event.subjectId(),
                event.teacherId(),
                event.slotId(),
                event.roomId(),
                event.lessonType(),
                event.lessonFormat(),
                event.onlineMeetingUrl(),
                null
        ));
        payload.put("overrideType", ScheduleOverrideTypeV1.CANCEL.name());
        payload.put("cancelReason", event.cancelReason());
        
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forRecipient(event.eventId(), recipientUserId, type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.SCHEDULE,
                "Lesson cancelled",
                scheduleCancelledBody(event.lessonType(), event.date()),
                payload
        );
    }
    
    public NotificationDraft fromScheduleLessonReplaced(ScheduleLessonReplacedEventV1 event, UUID recipientUserId) {
        NotificationType type = resolveScheduleReplacementType(event);
        Map<String, Object> payload = new LinkedHashMap<>(schedulePayload(
                event.eventId(),
                "SCHEDULE_OVERRIDE",
                event.overrideId(),
                event.date(),
                event.groupId(),
                event.subjectId(),
                event.newTeacherId(),
                event.newSlotId(),
                event.newRoomId(),
                event.lessonType(),
                event.newLessonFormat(),
                event.onlineMeetingUrl(),
                null
        ));
        payload.put("oldTeacherId", event.oldTeacherId());
        payload.put("newTeacherId", event.newTeacherId());
        payload.put("oldRoomId", event.oldRoomId());
        payload.put("newRoomId", event.newRoomId());
        payload.put("oldSlotId", event.oldSlotId());
        payload.put("newSlotId", event.newSlotId());
        payload.put("oldLessonFormat", event.oldLessonFormat() == null ? null : event.oldLessonFormat().name());
        payload.put("newLessonFormat", event.newLessonFormat() == null ? null : event.newLessonFormat().name());
        
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forRecipient(event.eventId(), recipientUserId, type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.SCHEDULE,
                scheduleReplacementTitle(type),
                scheduleReplacementBody(event.lessonType(), event.date()),
                payload
        );
    }
    
    public NotificationDraft fromScheduleExtraLessonCreated(ScheduleExtraLessonCreatedEventV1 event, UUID recipientUserId) {
        NotificationType type = NotificationType.SCHEDULE_EXTRA_LESSON;
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forRecipient(event.eventId(), recipientUserId, type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.SCHEDULE,
                "Extra lesson added",
                extraLessonBody(event.lessonType(), event.date()),
                schedulePayload(
                        event.eventId(),
                        "SCHEDULE_OVERRIDE",
                        event.overrideId(),
                        event.date(),
                        event.groupId(),
                        event.subjectId(),
                        event.teacherId(),
                        event.slotId(),
                        event.roomId(),
                        event.lessonType(),
                        event.lessonFormat(),
                        event.onlineMeetingUrl(),
                        event.notes()
                )
        );
    }
    
    public NotificationDraft fromAssignmentCreated(AssignmentCreatedEventV1 event, UUID recipientUserId) {
        NotificationType type = NotificationType.ASSIGNMENT_CREATED;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("relatedEntityType", "ASSIGNMENT");
        payload.put("relatedEntityId", event.assignmentId());
        payload.put("topicId", event.topicId());
        payload.put("title", event.title());
        payload.put("deadline", event.deadline());
        
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forRecipient(event.eventId(), recipientUserId, type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.ACADEMIC,
                "New assignment",
                "A new assignment \"" + event.title() + "\" was published.",
                payload
        );
    }
    
    public NotificationDraft fromAssignmentUpdated(AssignmentUpdatedEventV1 event, UUID recipientUserId) {
        NotificationType type = NotificationType.ASSIGNMENT_UPDATED;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("relatedEntityType", "ASSIGNMENT");
        payload.put("relatedEntityId", event.assignmentId());
        payload.put("topicId", event.topicId());
        payload.put("title", event.title());
        payload.put("deadline", event.deadline());
        payload.put("importantChangeType", event.importantChangeType().name());
        
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forRecipient(event.eventId(), recipientUserId, type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.ACADEMIC,
                "Assignment updated",
                assignmentUpdatedBody(event),
                payload
        );
    }
    
    public NotificationDraft fromGradeAssigned(GradeAssignedEventV1 event) {
        NotificationType type = NotificationType.GRADE_ASSIGNED;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("relatedEntityType", "GRADE");
        payload.put("relatedEntityId", event.gradeId());
        payload.put("submissionId", event.submissionId());
        payload.put("assignmentId", event.assignmentId());
        payload.put("score", event.score());
        payload.put("feedback", event.feedback());
        
        return new NotificationDraft(
                event.studentUserId(),
                notificationEventIdFactory.forRecipient(event.eventId(), event.studentUserId(), type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.ACADEMIC,
                "New grade",
                "Your work has been graded.",
                payload
        );
    }
    
    public NotificationDraft fromTestPublished(TestPublishedEventV1 event, UUID recipientUserId) {
        NotificationType type = NotificationType.TEST_PUBLISHED;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("relatedEntityType", "TEST");
        payload.put("relatedEntityId", event.testId());
        payload.put("topicId", event.topicId());
        payload.put("title", event.title());
        payload.put("availableFrom", event.availableFrom());
        payload.put("deadline", event.deadline());
        
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forRecipient(event.eventId(), recipientUserId, type),
                event.getClass().getSimpleName(),
                type,
                NotificationCategory.ACADEMIC,
                "New test",
                "A new test \"" + event.title() + "\" is now available.",
                payload
        );
    }
    
    public NotificationDraft assignmentDeadlineReminder(
            UUID recipientUserId,
            UUID assignmentId,
            String title,
            Instant deadline,
            Instant reminderAt
    ) {
        NotificationType type = NotificationType.ASSIGNMENT_DEADLINE_REMINDER;
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forReminder(recipientUserId, type, "ASSIGNMENT", assignmentId, reminderAt),
                "AssignmentDeadlineReminder",
                type,
                NotificationCategory.REMINDER,
                "Assignment deadline soon",
                "Assignment \"" + title + "\" is due at " + deadline + ".",
                Map.of(
                        "relatedEntityType", "ASSIGNMENT",
                        "relatedEntityId", assignmentId,
                        "deadline", deadline,
                        "reminderAt", reminderAt
                )
        );
    }
    
    public NotificationDraft testDeadlineReminder(
            UUID recipientUserId,
            UUID testId,
            String title,
            Instant deadline,
            Instant reminderAt
    ) {
        NotificationType type = NotificationType.TEST_DEADLINE_REMINDER;
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forReminder(recipientUserId, type, "TEST", testId, reminderAt),
                "TestDeadlineReminder",
                type,
                NotificationCategory.REMINDER,
                "Test deadline soon",
                "Test \"" + title + "\" is due at " + deadline + ".",
                Map.of(
                        "relatedEntityType", "TEST",
                        "relatedEntityId", testId,
                        "deadline", deadline,
                        "reminderAt", reminderAt
                )
        );
    }
    
    public NotificationDraft lessonReminder(
            UUID recipientUserId,
            UUID lessonId,
            Instant lessonStartAt,
            Instant reminderAt
    ) {
        NotificationType type = NotificationType.LESSON_REMINDER;
        return new NotificationDraft(
                recipientUserId,
                notificationEventIdFactory.forReminder(recipientUserId, type, "LESSON", lessonId, reminderAt),
                "LessonReminder",
                type,
                NotificationCategory.REMINDER,
                "Lesson reminder",
                "You have an upcoming lesson at " + lessonStartAt + ".",
                Map.of(
                        "relatedEntityType", "LESSON",
                        "relatedEntityId", lessonId,
                        "lessonStartAt", lessonStartAt,
                        "reminderAt", reminderAt
                )
        );
    }
    
    private NotificationType resolveScheduleReplacementType(ScheduleLessonReplacedEventV1 event) {
        boolean teacherChanged = !event.oldTeacherId().equals(event.newTeacherId());
        boolean roomChanged = !Objects.equals(event.oldRoomId(), event.newRoomId());
        boolean slotChanged = !event.oldSlotId().equals(event.newSlotId());
        boolean formatChanged = event.oldLessonFormat() != event.newLessonFormat();
        
        int changedCount = (teacherChanged ? 1 : 0) + (roomChanged ? 1 : 0) + (slotChanged ? 1 : 0) + (formatChanged ? 1 : 0);
        if (changedCount != 1) {
            return NotificationType.SCHEDULE_LESSON_CHANGED;
        }
        if (teacherChanged) {
            return NotificationType.SCHEDULE_TEACHER_CHANGED;
        }
        if (roomChanged) {
            return NotificationType.SCHEDULE_ROOM_CHANGED;
        }
        if (formatChanged) {
            return NotificationType.SCHEDULE_FORMAT_CHANGED;
        }
        return NotificationType.SCHEDULE_LESSON_CHANGED;
    }
    
    private String scheduleReplacementTitle(NotificationType type) {
        return switch (type) {
            case SCHEDULE_ROOM_CHANGED -> "Room changed";
            case SCHEDULE_FORMAT_CHANGED -> "Lesson format changed";
            case SCHEDULE_TEACHER_CHANGED -> "Teacher changed";
            default -> "Schedule changed";
        };
    }
    
    private String scheduleChangedBody(ScheduleLessonTypeV1 lessonType, LocalDate date) {
        return "Your " + lessonReference(lessonType) + " on " + date + " was updated. Check the latest details.";
    }
    
    private String scheduleCancelledBody(ScheduleLessonTypeV1 lessonType, LocalDate date) {
        return "Your " + lessonReference(lessonType) + " on " + date + " was cancelled.";
    }
    
    private String scheduleReplacementBody(ScheduleLessonTypeV1 lessonType, LocalDate date) {
        return "Your " + lessonReference(lessonType) + " on " + date + " was updated. Check the new time, room, teacher, or format.";
    }
    
    private String extraLessonBody(ScheduleLessonTypeV1 lessonType, LocalDate date) {
        String lessonTypeDisplayName = lessonTypeDisplayName(lessonType);
        if (lessonTypeDisplayName == null) {
            return "An extra lesson was added on " + date + ".";
        }
        
        return "An extra " + lessonTypeDisplayName + " was added on " + date + ".";
    }
    
    private String assignmentUpdatedBody(AssignmentUpdatedEventV1 event) {
        return switch (event.importantChangeType()) {
            case DEADLINE_CHANGED -> "Assignment \"" + event.title() + "\" has a new deadline.";
            case TITLE_CHANGED -> "Assignment details were updated. Check the latest title and instructions.";
            case DESCRIPTION_CHANGED -> "Assignment \"" + event.title() + "\" was updated. Check the latest details.";
        };
    }
    
    private Map<String, Object> schedulePayload(
            UUID eventId,
            String relatedEntityType,
            UUID relatedEntityId,
            LocalDate date,
            UUID groupId,
            UUID subjectId,
            UUID teacherId,
            UUID slotId,
            UUID roomId,
            ScheduleLessonTypeV1 lessonType,
            ScheduleLessonFormatV1 lessonFormat,
            String onlineMeetingUrl,
            String notes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("relatedEntityType", relatedEntityType);
        payload.put("relatedEntityId", relatedEntityId);
        payload.put("date", date);
        payload.put("groupId", groupId);
        payload.put("subjectId", subjectId);
        payload.put("teacherId", teacherId);
        payload.put("slotId", slotId);
        payload.put("roomId", roomId);
        payload.put("lessonType", lessonType == null ? null : lessonType.name());
        payload.put("lessonTypeDisplayName", lessonTypeDisplayName(lessonType));
        payload.put("lessonFormat", lessonFormat == null ? null : lessonFormat.name());
        payload.put("onlineMeetingUrl", onlineMeetingUrl);
        payload.put("notes", notes);
        return payload;
    }
    
    private String lessonReference(ScheduleLessonTypeV1 lessonType) {
        String lessonTypeDisplayName = lessonTypeDisplayName(lessonType);
        return lessonTypeDisplayName == null ? "lesson" : lessonTypeDisplayName;
    }
    
    private String lessonTypeDisplayName(ScheduleLessonTypeV1 lessonType) {
        return lessonType == null ? null : lessonType.getDisplayName();
    }
}
