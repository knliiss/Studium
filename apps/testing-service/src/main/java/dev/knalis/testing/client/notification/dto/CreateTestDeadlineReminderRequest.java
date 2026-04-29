package dev.knalis.testing.client.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateTestDeadlineReminderRequest(
        UUID testId,
        String title,
        Instant deadline,
        Instant reminderAt
) {
}
