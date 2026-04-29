package dev.knalis.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcademicReminderNotificationService {
    
    private final NotificationService notificationService;
    private final AcademicNotificationFactory academicNotificationFactory;
    
    public void createAssignmentDeadlineReminder(
            UUID userId,
            UUID assignmentId,
            String title,
            Instant deadline,
            Instant reminderAt
    ) {
        handleDraft(academicNotificationFactory.assignmentDeadlineReminder(userId, assignmentId, title, deadline, reminderAt));
    }
    
    public void createTestDeadlineReminder(
            UUID userId,
            UUID testId,
            String title,
            Instant deadline,
            Instant reminderAt
    ) {
        handleDraft(academicNotificationFactory.testDeadlineReminder(userId, testId, title, deadline, reminderAt));
    }
    
    public void createLessonReminder(UUID userId, UUID lessonId, Instant lessonStartAt, Instant reminderAt) {
        handleDraft(academicNotificationFactory.lessonReminder(userId, lessonId, lessonStartAt, reminderAt));
    }
    
    private void handleDraft(NotificationDraft draft) {
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
}
