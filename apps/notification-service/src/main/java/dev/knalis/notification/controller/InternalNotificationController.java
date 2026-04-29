package dev.knalis.notification.controller;

import dev.knalis.notification.dto.request.CreateAssignmentDeadlineReminderRequest;
import dev.knalis.notification.dto.request.CreateInternalNotificationRequest;
import dev.knalis.notification.dto.request.CreateTestDeadlineReminderRequest;
import dev.knalis.notification.dto.response.NotificationResponse;
import dev.knalis.notification.service.AcademicReminderNotificationService;
import dev.knalis.notification.service.InternalRequestGuard;
import dev.knalis.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {
    
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    
    private final NotificationService notificationService;
    private final AcademicReminderNotificationService academicReminderNotificationService;
    private final InternalRequestGuard internalRequestGuard;
    
    @PostMapping("/users/{userId}")
    public NotificationResponse createNotification(
            @PathVariable UUID userId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody CreateInternalNotificationRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return notificationService.createInternal(userId, request);
    }

    @PostMapping("/users/{userId}/reminders/assignments/deadline")
    public void createAssignmentDeadlineReminder(
            @PathVariable UUID userId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody CreateAssignmentDeadlineReminderRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        academicReminderNotificationService.createAssignmentDeadlineReminder(
                userId,
                request.assignmentId(),
                request.title(),
                request.deadline(),
                request.reminderAt()
        );
    }

    @PostMapping("/users/{userId}/reminders/tests/deadline")
    public void createTestDeadlineReminder(
            @PathVariable UUID userId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody CreateTestDeadlineReminderRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        academicReminderNotificationService.createTestDeadlineReminder(
                userId,
                request.testId(),
                request.title(),
                request.deadline(),
                request.reminderAt()
        );
    }
}
