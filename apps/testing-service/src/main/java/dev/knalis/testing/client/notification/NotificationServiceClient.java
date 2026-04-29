package dev.knalis.testing.client.notification;

import java.time.Instant;
import java.util.UUID;

public interface NotificationServiceClient {

    void createTestDeadlineReminder(
            UUID userId,
            UUID testId,
            String title,
            Instant deadline,
            Instant reminderAt
    );
}
