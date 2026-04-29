package dev.knalis.assignment.client.notification;

import java.time.Instant;
import java.util.UUID;

public interface NotificationServiceClient {

    void createAssignmentDeadlineReminder(
            UUID userId,
            UUID assignmentId,
            String title,
            Instant deadline,
            Instant reminderAt
    );
}
